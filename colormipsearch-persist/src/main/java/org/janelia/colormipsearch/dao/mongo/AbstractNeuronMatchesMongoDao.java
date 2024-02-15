package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Preconditions;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.UnwindOptions;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.WriteModel;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.conversions.Bson;
import org.janelia.colormipsearch.dao.EntityFieldNameValueHandler;
import org.janelia.colormipsearch.dao.EntityFieldValueHandler;
import org.janelia.colormipsearch.dao.EntityUtils;
import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.dao.NeuronMatchesDao;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dao.NeuronsMatchFilter;
import org.janelia.colormipsearch.dao.SetFieldValueHandler;
import org.janelia.colormipsearch.dao.SetOnCreateValueHandler;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.datarequests.PagedResult;
import org.janelia.colormipsearch.model.AbstractMatchEntity;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.EntityField;

abstract class AbstractNeuronMatchesMongoDao<R extends AbstractMatchEntity<? extends AbstractNeuronEntity,
                                                                           ? extends AbstractNeuronEntity>> extends AbstractMongoDao<R>
                                                                                                            implements NeuronMatchesDao<R> {

    protected AbstractNeuronMatchesMongoDao(MongoClient mongoClient, MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        super(mongoClient, mongoDatabase, idGenerator);
        createDocumentIndexes();
    }

    @Override
    protected void createDocumentIndexes() {
        mongoCollection.createIndex(Indexes.ascending("class"));
        mongoCollection.createIndex(Indexes.ascending("maskImageRefId"));
        mongoCollection.createIndex(Indexes.ascending("matchedImageRefId"));
        mongoCollection.createIndex(Indexes.ascending("tags"));
        mongoCollection.createIndex(Indexes.ascending("maskImageRefId", "matchedImageRefId"));
    }

    @Override
    public R findByEntityId(Number id) {
        List<R> results = findNeuronMatches(
                new NeuronsMatchFilter<R>().setMatchEntityIds(Collections.singletonList(id)),
                null,
                null,
                null,
                0,
                -1
        );
        if (results.isEmpty()) {
            return null;
        } else {
            return results.get(0);
        }
    }

    @Override
    public List<R> findByEntityIds(Collection<Number> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        } else {
            return findNeuronMatches(
                    new NeuronsMatchFilter<R>().setMatchEntityIds(ids),
                    null,
                    null,
                    null,
                    0,
                    -1
            );
        }
    }

    @Override
    public PagedResult<R> findAll(Class<R> type, PagedRequest pageRequest) {
        return new PagedResult<>(
                pageRequest,
                findNeuronMatches(
                        new NeuronsMatchFilter<R>().setMatchEntityType(type),
                        null,
                        null,
                        MongoDaoHelper.createBsonSortCriteria(pageRequest.getSortCriteria()),
                        pageRequest.getOffset(),
                        pageRequest.getPageSize()
                )
        );
    }

    @Override
    public long createOrUpdateAll(List<R> matches, List<Function<R, EntityField<?>>> fieldsToUpdateSelectors) {
        List<WriteModel<R>> toWrite = new ArrayList<>();

        matches.forEach(m -> {
            if (!m.hasEntityId() && !hasBothImageRefs(m)) {
                m.setEntityId(idGenerator.generateId());
                m.setCreatedDate(new Date());
                toWrite.add(new InsertOneModel<>(m));
            } else {
                Bson selectCriteria;
                Stream<EntityFieldNameValueHandler<?>> onCreateSetters;

                UpdateOptions updateOptions = new UpdateOptions();
                if (m.hasEntityId()) {
                    // select by entity ID
                    selectCriteria = MongoDaoHelper.createFilterById(m.getEntityId());
                    onCreateSetters = Stream.of();
                } else {
                    m.setEntityId(idGenerator.generateId());
                    m.setCreatedDate(new Date());
                    updateOptions.upsert(true);
                    updateOptions.bypassDocumentValidation(false);
                    // select by Image Ref IDs
                    selectCriteria = MongoDaoHelper.createBsonFilterCriteria(
                            Arrays.asList(
                                    MongoDaoHelper.createEqFilter("maskImageRefId", m.getMaskImageRefId()),
                                    MongoDaoHelper.createEqFilter("matchedImageRefId", m.getMatchedImageRefId())
                            )
                    );
                    onCreateSetters = Stream.of(
                            new EntityFieldNameValueHandler<>("_id", new SetOnCreateValueHandler<>(m.getEntityId())),
                            new EntityFieldNameValueHandler<>("class", new SetOnCreateValueHandler<>(m.getEntityClass())),
                            new EntityFieldNameValueHandler<>("createdDate", new SetOnCreateValueHandler<>(m.getCreatedDate()))
                    );
                }
                Bson updates = getUpdates(
                        Stream.concat(
                                onCreateSetters,
                                fieldsToUpdateSelectors.stream()
                                        .map(fieldSelector -> fieldSelector.apply(m))
                                        .map(MongoDaoHelper::entityFieldToValueHandler)
                        ).collect(Collectors.toMap(
                                EntityFieldNameValueHandler::getFieldName,
                                EntityFieldNameValueHandler::getValueHandler))
                );
                toWrite.add(new UpdateOneModel<R>(selectCriteria, updates, updateOptions));
            }
        });
        BulkWriteResult result = mongoCollection.bulkWrite(
                toWrite,
                new BulkWriteOptions().bypassDocumentValidation(false).ordered(false));
        return result.getInsertedCount() + result.getMatchedCount();
    }

    @Override
    public long updateAll(NeuronsMatchFilter<R> neuronsMatchFilter,
                          Map<String, EntityFieldValueHandler<?>> fieldsToUpdate) {
        if (neuronsMatchFilter != null && !neuronsMatchFilter.isEmpty() && !fieldsToUpdate.isEmpty()) {
            List<WriteModel<R>> toWrite = new ArrayList<>();
            UpdateOptions updateOptions = new UpdateOptions();
            toWrite.add(
                    new UpdateManyModel<R>(
                            NeuronSelectionHelper.getNeuronsMatchFilter(neuronsMatchFilter),
                            getUpdates(fieldsToUpdate),
                            updateOptions
                    ));
            BulkWriteResult result = mongoCollection.bulkWrite(
                    toWrite,
                    new BulkWriteOptions().bypassDocumentValidation(false).ordered(false));
            return result.getModifiedCount();
        } else {
            return -1; // do not apply the update across the board
        }
    }

    @Override
    public long updateExistingMatches(List<R> matches, List<Function<R, Pair<String, ?>>> fieldsToUpdateSelectors) {
        List<WriteModel<R>> toWrite = new ArrayList<>();

        matches.forEach(m -> {
            Preconditions.checkArgument(m.hasEntityId());
            UpdateOptions updateOptions = new UpdateOptions();
            updateOptions.upsert(false);
            Bson updates = getUpdates(
                fieldsToUpdateSelectors.stream()
                        .map(fieldSelector -> fieldSelector.apply(m))
                        .map(this::fieldValueToEntityFieldValueHandler)
                        .collect(Collectors.toMap(
                            EntityFieldNameValueHandler::getFieldName,
                            EntityFieldNameValueHandler::getValueHandler))
            );
            toWrite.add(new UpdateOneModel<R>(
                MongoDaoHelper.createFilterById(m.getEntityId()),
                updates,
                updateOptions
            ));
        });
        if (toWrite.size() > 0 ) {
            BulkWriteResult result = mongoCollection.bulkWrite(
                    toWrite,
                    new BulkWriteOptions().bypassDocumentValidation(false).ordered(false));
            return result.getMatchedCount();
        } else {
            return 0;
        }
    }

    private <V> EntityFieldNameValueHandler<V> fieldValueToEntityFieldValueHandler(Pair<String, V> nf) {
        return new EntityFieldNameValueHandler<>(
                nf.getLeft(),
                new SetFieldValueHandler<>(nf.getRight()));
    }

    @Override
    public long countNeuronMatches(NeuronsMatchFilter<R> neuronsMatchFilter,
                                   NeuronSelector maskSelector,
                                   NeuronSelector targetSelector) {
        return MongoDaoHelper.countAggregate(
                createQueryPipeline(
                        neuronsMatchFilter,
                        maskSelector,
                        targetSelector),
                mongoCollection);
    }

    @Override
    public PagedResult<R> findNeuronMatches(NeuronsMatchFilter<R> neuronsMatchFilter,
                                            NeuronSelector maskSelector,
                                            NeuronSelector targetSelector,
                                            PagedRequest pageRequest) {
        return new PagedResult<>(
                pageRequest,
                findNeuronMatches(
                        neuronsMatchFilter,
                        maskSelector,
                        targetSelector,
                        MongoDaoHelper.createBsonSortCriteria(pageRequest.getSortCriteria()),
                        pageRequest.getOffset(),
                        pageRequest.getPageSize()
                )
        );
    }

    private List<R> findNeuronMatches(NeuronsMatchFilter<R> matchFilter,
                                      NeuronSelector maskImageFilter,
                                      NeuronSelector targetImageFilter,
                                      Bson sortCriteria,
                                      long offset, int length) {
        return MongoDaoHelper.aggregateAsList(
                createQueryPipeline(matchFilter, maskImageFilter, targetImageFilter),
                sortCriteria,
                offset,
                length,
                mongoCollection,
                getEntityType(),
                true);
    }

    protected List<Bson> createQueryPipeline(NeuronsMatchFilter<R> matchFilter,
                                             NeuronSelector maskImageFilter,
                                             NeuronSelector targetImageFilter) {
        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.match(NeuronSelectionHelper.getNeuronsMatchFilter(matchFilter)));
        pipeline.add(Aggregates.lookup(
                EntityUtils.getPersistenceInfo(AbstractNeuronEntity.class).storeName(),
                "maskImageRefId",
                "_id",
                "maskImage"
        ));
        pipeline.add(Aggregates.lookup(
                EntityUtils.getPersistenceInfo(AbstractNeuronEntity.class).storeName(),
                "matchedImageRefId",
                "_id",
                "image" // matchedImage field name is 'image'
        ));
        UnwindOptions unwindOptions = new UnwindOptions().preserveNullAndEmptyArrays(true);
        pipeline.add(Aggregates.unwind("$maskImage", unwindOptions));
        pipeline.add(Aggregates.unwind("$image", unwindOptions));
        if (maskImageFilter != null && !maskImageFilter.isEmpty()) {
            pipeline.add(Aggregates.match(NeuronSelectionHelper.getNeuronFilter("maskImage", maskImageFilter)));
        }
        if (targetImageFilter != null && !targetImageFilter.isEmpty()) {
            pipeline.add(Aggregates.match(NeuronSelectionHelper.getNeuronFilter("image", targetImageFilter)));
        }
        return pipeline;
    }

    private boolean hasBothImageRefs(R match) {
        return match.hasMaskImageRefId() && match.hasMatchedImageRefId();
    }

}
