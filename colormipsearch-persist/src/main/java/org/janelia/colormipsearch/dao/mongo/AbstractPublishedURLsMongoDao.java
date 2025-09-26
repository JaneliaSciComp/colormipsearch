package org.janelia.colormipsearch.dao.mongo;

import com.mongodb.client.MongoDatabase;

import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.dao.PublishedURLsDao;
import org.janelia.colormipsearch.model.AbstractPublishedURLs;

public abstract class AbstractPublishedURLsMongoDao<T extends AbstractPublishedURLs> extends AbstractMongoDao<T> implements PublishedURLsDao<T> {

    public AbstractPublishedURLsMongoDao(MongoDatabase mongoDatabase, IdGenerator idGenerator, boolean skipIndexCreation) {
        super(mongoDatabase, idGenerator);
        createDocumentIndexes(!skipIndexCreation);
    }

    @Override
    protected void createDocumentIndexes(boolean createAllIndexes) {
        // do nothing here
    }
}
