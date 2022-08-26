package org.janelia.colormipsearch.dao.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;

public class CDMatchesMongoDao<R extends CDMatchEntity<? extends AbstractNeuronEntity,
                                                       ? extends AbstractNeuronEntity>> extends AbstractNeuronMatchesMongoDao<R> {
    public CDMatchesMongoDao(MongoClient mongoClient,MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        super(mongoClient, mongoDatabase, idGenerator);
    }
}
