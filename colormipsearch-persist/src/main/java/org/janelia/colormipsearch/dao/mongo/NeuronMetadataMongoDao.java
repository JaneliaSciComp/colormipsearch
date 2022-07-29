package org.janelia.colormipsearch.dao.mongo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.FindOneAndReplaceOptions;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReturnDocument;

import org.bson.conversions.Bson;
import org.janelia.colormipsearch.dao.EntityFieldValueHandler;
import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.dao.NeuronMetadataDao;
import org.janelia.colormipsearch.dao.NeuronSelector;
import org.janelia.colormipsearch.dao.SetFieldValueHandler;
import org.janelia.colormipsearch.dao.SetOnCreateValueHandler;
import org.janelia.colormipsearch.datarequests.PagedRequest;
import org.janelia.colormipsearch.datarequests.PagedResult;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.ComputeFileType;

public class NeuronMetadataMongoDao<N extends AbstractNeuronEntity> extends AbstractMongoDao<N>
                                                                      implements NeuronMetadataDao<N> {
    public NeuronMetadataMongoDao(MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        super(mongoDatabase, idGenerator);
        createDocumentIndexes();
    }

    @Override
    protected void createDocumentIndexes() {
        mongoCollection.createIndex(Indexes.hashed("class"));
        mongoCollection.createIndex(Indexes.hashed("libraryName"));
        mongoCollection.createIndex(Indexes.hashed("publishedName"));
        mongoCollection.createIndex(Indexes.hashed("id"));
    }

    @Override
    public N createOrUpdate(N neuron) {
        FindOneAndReplaceOptions updateOptions = new FindOneAndReplaceOptions();
        updateOptions.upsert(true);
        updateOptions.returnDocument(ReturnDocument.AFTER);
        N toUpdate;
        if (neuron.hasEntityId()) {
            toUpdate = neuron;
        } else if (isIdentifiable(neuron)) {
            toUpdate = minimalCreateOrUpdate(neuron);
        } else {
            save(neuron);
            return neuron;
        }
        return mongoCollection.findOneAndReplace(
                MongoDaoHelper.createFilterById(toUpdate.getEntityId()),
                neuron,
                updateOptions
        );
    }

    private N minimalCreateOrUpdate(N neuron) {
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
            selectFilters.add(MongoDaoHelper.createEqFilter("id", neuron.getMipId()));
            updates.add(MongoDaoHelper.getFieldUpdate("id", new SetOnCreateValueHandler<>(neuron.getMipId())));
        }
        selectFilters.add(MongoDaoHelper.createEqFilter(
                "computeFiles.InputColorDepthImage",
                neuron.getComputeFileName(ComputeFileType.InputColorDepthImage))
        );
        selectFilters.add(MongoDaoHelper.createEqFilter(
                "computeFiles.SourceColorDepthImage",
                neuron.getComputeFileName(ComputeFileType.SourceColorDepthImage))
        );
        updates.add(MongoDaoHelper.getFieldUpdate("computeFiles.InputColorDepthImage",
                new SetOnCreateValueHandler<>(neuron.getComputeFileData(ComputeFileType.InputColorDepthImage))));
        updates.add(MongoDaoHelper.getFieldUpdate("computeFiles.SourceColorDepthImage",
                new SetOnCreateValueHandler<>(neuron.getComputeFileData(ComputeFileType.SourceColorDepthImage))));

        N updatedNeuron = mongoCollection.findOneAndUpdate(
                MongoDaoHelper.createBsonFilterCriteria(selectFilters),
                MongoDaoHelper.combineUpdates(updates),
                updateOptions
        );
        neuron.setEntityId(updatedNeuron.getEntityId());
        neuron.setCreatedDate(updatedNeuron.getCreatedDate());
        return neuron;
    }

    private boolean isIdentifiable(N neuron) {
        return neuron.hasComputeFile(ComputeFileType.InputColorDepthImage)
            && neuron.hasComputeFile(ComputeFileType.SourceColorDepthImage);
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
                        getEntityType()
                )
        );
    }

    private List<Bson> createQueryPipeline(Bson matchFilter) {
        return Collections.singletonList(Aggregates.match(matchFilter));
    }
}
