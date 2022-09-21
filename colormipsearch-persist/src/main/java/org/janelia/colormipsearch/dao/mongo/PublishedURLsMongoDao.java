package org.janelia.colormipsearch.dao.mongo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.dao.PublishedURLsDao;
import org.janelia.colormipsearch.model.PublishedURLs;

public class PublishedURLsMongoDao extends AbstractMongoDao<PublishedURLs> implements PublishedURLsDao {

    public PublishedURLsMongoDao(MongoClient mongoClient, MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        super(mongoClient, mongoDatabase, idGenerator);
        createDocumentIndexes();
    }

    @Override
    protected void createDocumentIndexes() {
        // do nothing here
    }
}
