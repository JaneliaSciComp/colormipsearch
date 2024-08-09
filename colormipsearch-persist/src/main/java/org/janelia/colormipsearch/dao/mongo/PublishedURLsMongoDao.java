package org.janelia.colormipsearch.dao.mongo;

import com.mongodb.reactivestreams.client.MongoDatabase;

import org.janelia.colormipsearch.dao.IdGenerator;
import org.janelia.colormipsearch.model.NeuronPublishedURLs;

public class PublishedURLsMongoDao extends AbstractPublishedURLsMongoDao<NeuronPublishedURLs> {
    public PublishedURLsMongoDao(MongoDatabase mongoDatabase, IdGenerator idGenerator) {
        super(mongoDatabase, idGenerator);
    }
}
