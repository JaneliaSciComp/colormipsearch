package org.janelia.colormipsearch.dao.mongo;

import com.mongodb.reactivestreams.client.MongoDatabase;
import com.mongodb.client.model.Indexes;

import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.PPPMatchEntity;

public class PPPMatchesMongoDao<R extends PPPMatchEntity<? extends AbstractNeuronEntity,
                                                         ? extends AbstractNeuronEntity>> extends AbstractNeuronMatchesMongoDao<R> {
    public PPPMatchesMongoDao(MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        super(mongoDatabase, idGenerator);
    }

    @Override
    protected void createDocumentIndexes() {
        super.createDocumentIndexes();
        MongoDaoHelper.createIndex(Indexes.hashed("sourceEmLibrary"), mongoCollection);
        MongoDaoHelper.createIndex(Indexes.hashed("sourceLmLibrary"), mongoCollection);
        MongoDaoHelper.createIndex(Indexes.hashed("sourceEmName"), mongoCollection);
        MongoDaoHelper.createIndex(Indexes.ascending("sourceLmName"), mongoCollection);
    }

}
