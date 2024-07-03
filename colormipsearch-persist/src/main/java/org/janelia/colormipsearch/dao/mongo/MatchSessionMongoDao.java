package org.janelia.colormipsearch.dao.mongo;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;

import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.dao.MatchSessionDao;
import org.janelia.colormipsearch.model.AbstractSessionEntity;

public class MatchSessionMongoDao<T extends AbstractSessionEntity> extends AbstractMongoDao<T>
                                                                   implements MatchSessionDao<T> {
    public MatchSessionMongoDao(MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        super(mongoDatabase, idGenerator);
        createDocumentIndexes();
    }

    @Override
    protected void createDocumentIndexes() {
        mongoCollection.createIndex(Indexes.hashed("class"));
    }

}
