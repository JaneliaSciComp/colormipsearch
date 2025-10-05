package org.janelia.colormipsearch.dao.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;

import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.PPPMatchEntity;

public class PPPMatchesMongoDao<R extends PPPMatchEntity<? extends AbstractNeuronEntity,
                                                         ? extends AbstractNeuronEntity>> extends AbstractNeuronMatchesMongoDao<R> {
    public PPPMatchesMongoDao(MongoDatabase mongoDatabase, IdGenerator idGenerator, boolean skipIndexCreation) {
        super(mongoDatabase, idGenerator, skipIndexCreation);
    }

    @Override
    protected void createDocumentIndexes(boolean createAllIndexes) {
        if (!createAllIndexes) {
            return;
        }
        super.createDocumentIndexes(createAllIndexes);
        mongoCollection.createIndex(Indexes.hashed("sourceEmLibrary"));
        mongoCollection.createIndex(Indexes.hashed("sourceLmLibrary"));
        mongoCollection.createIndex(Indexes.hashed("sourceEmName"));
        mongoCollection.createIndex(Indexes.ascending("sourceLmName"));
    }

}
