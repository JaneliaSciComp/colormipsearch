package org.janelia.colormipsearch.dao.mongo;

import com.mongodb.client.MongoDatabase;

import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.model.PPPmURLs;

public class PPPmURLsMongoDao extends AbstractPublishedURLsMongoDao<PPPmURLs> {
    public PPPmURLsMongoDao(MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        super(mongoDatabase, idGenerator);
    }
}
