package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.janelia.colormipsearch.dao.AppendFieldValueHandler;
import org.janelia.colormipsearch.dao.EntityFieldValueHandler;
import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dao.SetFieldValueHandler;
import org.janelia.colormipsearch.dao.SetOnCreateValueHandler;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.datarequests.PagedResult;
import org.janelia.colormipsearch.datarequests.SortCriteria;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;
import org.janelia.colormipsearch.model.ProcessingType;

public class NeuronMetadataMongoDao<N extends AbstractNeuronEntity> extends AbstractMongoDao<N>
        implements NeuronMetadataDao<N> {
    private static final int MAX_UPDATE_RETRIES = 3;

    private final ClientSession session;

    public NeuronMetadataMongoDao(MongoClient mongoClient, MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        super(mongoClient, mongoDatabase, idGenerator);
        createDocumentIndexes();
        session = mongoClient.startSession();
    }

    @Override
    protected void createDocumentIndexes() {
        mongoCollection.createIndex(Indexes.hashed("class"));
        mongoCollection.createIndex(Indexes.hashed("libraryName"));
        mongoCollection.createIndex(Indexes.hashed("publishedName"));
        mongoCollection.createIndex(Indexes.hashed("mipId"));
        mongoCollection.createIndex(Indexes.ascending("tags"));
    }

    @Override
    public N createOrUpdate(N neuron) {
        if (isIdentifiable(neuron)) {
            return findAndUpdate(neuron);
        } else {
            save(neuron);
            return neuron;
        }
    }

    private N findAndUpdate(N neuron) {
        FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
        updateOptions.upsert(true);
        updateOptions.returnDocument(ReturnDocument.AFTER);

        List<Bson> selectFilters = new ArrayList<>();
        List<Bson> updates = new ArrayList<>();

        if (!neuron.hasEntityId()) {
            selectFilters.add(MongoDaoHelper.createFilterByClass(neuron.getClass()));
            updates.add(MongoDaoHelper.getFieldUpdate("_id", new SetOnCreateValueHandler<>(idGenerator.generateId())));
            updates.add(MongoDaoHelper.getFieldUpdate("createdDate", new SetOnCreateValueHandler<>(new Date())));
        } else {
            selectFilters.add(MongoDaoHelper.createFilterById(neuron.getEntityId()));
        }
        if (neuron.hasMipID()) {
            selectFilters.add(MongoDaoHelper.createEqFilter("mipId", neuron.getMipId()));
            updates.add(MongoDaoHelper.getFieldUpdate("mipId", new SetOnCreateValueHandler<>(neuron.getMipId())));
        }
        selectFilters.add(MongoDaoHelper.createEqFilter(
                "computeFiles.InputColorDepthImage",
                neuron.getComputeFileName(ComputeFileType.InputColorDepthImage))
        );
        selectFilters.add(MongoDaoHelper.createEqFilter(
                "computeFiles.SourceColorDepthImage",
                neuron.getComputeFileName(ComputeFileType.SourceColorDepthImage))
        );
        neuron.updateableFieldValues().forEach((fn, fv) -> {
            if (fv instanceof Iterable) {
                updates.add(MongoDaoHelper.getFieldUpdate(fn, new AppendFieldValueHandler<>(fv)));
            } else {
                updates.add(MongoDaoHelper.getFieldUpdate(fn, new SetFieldValueHandler<>(fv)));
            }
        });
        for (int i = 0; ; i++) {
            try {
                N updatedNeuron = mongoCollection
                        .withReadConcern(ReadConcern.LINEARIZABLE)
                        .withWriteConcern(WriteConcern.MAJORITY)
                        .withReadPreference(ReadPreference.primaryPreferred())
                        .findOneAndUpdate(
                                session,
                                MongoDaoHelper.createBsonFilterCriteria(selectFilters),
                                MongoDaoHelper.combineUpdates(updates),
                                updateOptions
                        );
                neuron.setEntityId(updatedNeuron.getEntityId());
                neuron.setCreatedDate(updatedNeuron.getCreatedDate());
                return updatedNeuron;
            } catch (Exception e) {
                if (i >= MAX_UPDATE_RETRIES) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private boolean isIdentifiable(N neuron) {
        return neuron.hasEntityId() ||
                neuron.hasComputeFile(ComputeFileType.InputColorDepthImage) && neuron.hasComputeFile(ComputeFileType.SourceColorDepthImage);
    }

    @Override
    public PagedResult<N> findNeurons(NeuronSelector neuronSelector, PagedRequest pageRequest) {
        return new PagedResult<>(
                pageRequest,
                MongoDaoHelper.aggregateAsList(
                        createQueryPipeline(NeuronSelectionHelper.getNeuronFilter(null, neuronSelector)),
                        MongoDaoHelper.createBsonSortCriteria(pageRequest.getSortCriteria()),
                        pageRequest.getOffset(),
                        pageRequest.getPageSize(),
                        mongoCollection,
                        getEntityType(),
                        true
                )
        );
    }

    @Override
    public PagedResult<Map<String, Object>> findDistinctNeuronAttributeValues(List<String> attributeNames, NeuronSelector neuronSelector, PagedRequest pagedRequest) {
        List<Document> selectedNeuronDocuments = MongoDaoHelper.aggregateAsList(
                Arrays.asList(
                        Aggregates.match(NeuronSelectionHelper.getNeuronFilter(null, neuronSelector)),
                        Aggregates.group(
                                MongoDaoHelper.distinctAttributesExpr(attributeNames),
                                attributeNames.stream()
                                        .map(attr -> MongoDaoHelper.createGroupResultExpression(attr, MongoDaoHelper.createFirstExpression(attr)))
                                        .collect(Collectors.toList())
                        ),
                        Aggregates.project(Projections.fields(
                                Stream.concat(
                                        Stream.of(Projections.excludeId()),
                                        attributeNames.stream().map(Projections::include)
                                ).collect(Collectors.toList())
                        ))
                ),
                MongoDaoHelper.createBsonSortCriteria(
                        attributeNames.stream().map(SortCriteria::new).collect(Collectors.toList())),
                pagedRequest.getOffset(),
                pagedRequest.getPageSize(),
                mongoCollection,
                Document.class,
                true
        );
        return new PagedResult<>(pagedRequest, new ArrayList<>(selectedNeuronDocuments));
    }

    private List<Bson> createQueryPipeline(Bson matchFilter) {
        return Collections.singletonList(Aggregates.match(matchFilter));
    }

    @Override
    public void addProcessingTagsToMIPIDs(Collection<String> neuronMIPIds, ProcessingType processingType, Set<String> tags) {
        if (CollectionUtils.isEmpty(neuronMIPIds) || processingType == null || CollectionUtils.isEmpty(tags)) {
            // don't do anything if neuronIds or tags are empty or if the processing type is not specified
            return;
        }
        Map<String, EntityFieldValueHandler<?>> toUpdate = ImmutableMap.of(
                "processedTags." + processingType.name(),
                new AppendFieldValueHandler<>(tags)
        );
        mongoCollection.updateMany(
                MongoDaoHelper.createInFilter("mipId", neuronMIPIds),
                getUpdates(toUpdate)
        );
    }
}
