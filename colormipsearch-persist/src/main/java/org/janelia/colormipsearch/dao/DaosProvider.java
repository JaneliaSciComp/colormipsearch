package org.janelia.colormipsearch.dao;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;

import org.janelia.colormipsearch.config.Config;
import org.janelia.colormipsearch.dao.mongo.CDMatchesMongoDao;
import org.janelia.colormipsearch.dao.mongo.MatchSessionMongoDao;
import org.janelia.colormipsearch.dao.mongo.NeuronMetadataMongoDao;
import org.janelia.colormipsearch.dao.mongo.PPPMatchesMongoDao;
import org.janelia.colormipsearch.dao.mongo.PPPmURLsMongoDao;
import org.janelia.colormipsearch.dao.mongo.PublishedLMImageMongoDao;
import org.janelia.colormipsearch.dao.mongo.PublishedURLsMongoDao;
import org.janelia.colormipsearch.dao.mongo.support.MongoDBHelper;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.AbstractSessionEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.NeuronPublishedURLs;
import org.janelia.colormipsearch.model.PPPMatchEntity;
import org.janelia.colormipsearch.model.PPPmURLs;

public class DaosProvider {

    private static DaosProvider instance;

    public static synchronized DaosProvider getInstance(Config config) {
        if (instance == null) {
            instance = new DaosProvider(config);
        }
        return instance;
    }

    private final MongoDatabase mongoDatabase;
    private final IdGenerator idGenerator;

    private DaosProvider(Config config) {
        MongoClient mongoClient = MongoDBHelper.createMongoClient(config.getStringPropertyValue("MongoDB.ConnectionURL"),
                config.getStringPropertyValue("MongoDB.Server"),
                config.getStringPropertyValue("MongoDB.AuthDatabase"),
                config.getStringPropertyValue("MongoDB.Username"),
                config.getStringPropertyValue("MongoDB.Password"),
                config.getStringPropertyValue("MongoDB.ReplicaSet"),
                config.getBooleanPropertyValue("MongoDB.UseSSL"),
                config.getIntegerPropertyValue("MongoDB.Connections", 0), // connections per host
                config.getIntegerPropertyValue("MongoDB.ConnectionTimeoutMillis", 0),
                config.getIntegerPropertyValue("MongoDB.MaxConnecting", 0),
                config.getIntegerPropertyValue("MongoDB.MaxConnectTimeSecs", 0),
                config.getIntegerPropertyValue("MongoDB.MaxConnectionIdleSecs", 0),
                config.getIntegerPropertyValue("MongoDB.MaxConnectionLifeSecs", 0)
        );
        this.mongoDatabase = MongoDBHelper.createMongoDatabase(mongoClient, config.getStringPropertyValue("MongoDB.Database"));
        this.idGenerator = new TimebasedIdGenerator(config.getIntegerPropertyValue("TimebasedId.Context", 0));
    }

    public <T extends AbstractSessionEntity> MatchSessionDao<T>
    getMatchParametersDao() {
        return new MatchSessionMongoDao<>(mongoDatabase, idGenerator);
    }

    public <R extends CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> NeuronMatchesDao<R>
    getCDMatchesDao() {
        return new CDMatchesMongoDao<>(mongoDatabase, idGenerator);
    }

    public <R extends PPPMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> NeuronMatchesDao<R>
    getPPPMatchesDao() {
        return new PPPMatchesMongoDao<>(mongoDatabase, idGenerator);
    }

    public <N extends AbstractNeuronEntity> NeuronMetadataDao<N>
    getNeuronMetadataDao() {
        return new NeuronMetadataMongoDao<>(mongoDatabase, idGenerator);
    }

    public PublishedLMImageDao getPublishedImageDao() {
        return new PublishedLMImageMongoDao(mongoDatabase, idGenerator);
    }

    public PublishedURLsDao<NeuronPublishedURLs> getNeuronPublishedUrlsDao() {
        return new PublishedURLsMongoDao(mongoDatabase, idGenerator);
    }

    public PublishedURLsDao<PPPmURLs> getPPPmUrlsDao() {
        return new PPPmURLsMongoDao(mongoDatabase, idGenerator);
    }

}
