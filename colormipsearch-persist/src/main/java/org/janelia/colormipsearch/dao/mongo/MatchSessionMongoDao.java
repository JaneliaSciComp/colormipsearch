package org.janelia.colormipsearch.dao.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;

import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.dao.MatchSessionDao;
import org.janelia.colormipsearch.model.AbstractSessionEntity;

public class MatchSessionMongoDao<T extends AbstractSessionEntity> extends AbstractMongoDao<T>
                                                                   implements MatchSessionDao<T> {
    public MatchSessionMongoDao(MongoDatabase mongoDatabase, IdGenerator idGenerator, boolean skipIndexCreation) {
        super(mongoDatabase, idGenerator);
        createDocumentIndexes(skipIndexCreation);
    }

    @Override
    protected void createDocumentIndexes(boolean createAllIndexes) {
        if (!createAllIndexes) return;

        mongoCollection.createIndex(Indexes.hashed("class"));
    }

}
