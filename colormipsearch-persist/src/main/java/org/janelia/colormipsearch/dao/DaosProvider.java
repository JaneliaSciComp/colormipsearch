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
import org.janelia.colormipsearch.dao.mongo.support.MongoClientProvider;
import org.janelia.colormipsearch.model.AbstractNeuronEntity;
import org.janelia.colormipsearch.model.AbstractSessionEntity;
import org.janelia.colormipsearch.model.CDMatchEntity;
import org.janelia.colormipsearch.model.NeuronPublishedURLs;
import org.janelia.colormipsearch.model.PPPMatchEntity;
import org.janelia.colormipsearch.model.PPPmURLs;

public class DaosProvider {

    private static DaosProvider instance;

    public static synchronized DaosProvider getInstance(Config config, boolean useIDGeneratorLock) {
        if (instance == null) {
            instance = new DaosProvider(config, useIDGeneratorLock);
        }
        return instance;
    }

    private final MongoDatabase mongoDatabase;
    private final IdGenerator idGenerator;
    private final boolean skipIndexCreation;

    private DaosProvider(Config config, boolean useIDGeneratorLock) {
        MongoClient mongoClient = MongoClientProvider.createMongoClient(
                config.getStringPropertyValue("MongoDB.ConnectionURL"),
                config.getStringPropertyValue("MongoDB.Server"),
                config.getStringPropertyValue("MongoDB.AuthDatabase"),
                config.getStringPropertyValue("MongoDB.Username"),
                config.getStringPropertyValue("MongoDB.Password"),
                config.getStringPropertyValue("MongoDB.ReplicaSet"),
                config.getBooleanPropertyValue("MongoDB.UseSSL"),
                config.getBooleanPropertyValue("MongoDB.PreferSecondaryForReads", false),
                config.getIntegerPropertyValue("MongoDB.Connections", 0), // connections per host
                config.getIntegerPropertyValue("MongoDB.ConnectionTimeoutMillis", 0),
                config.getIntegerPropertyValue("MongoDB.MaxConnecting", 0),
                config.getIntegerPropertyValue("MongoDB.MaxConnectTimeSecs", 0),
                config.getIntegerPropertyValue("MongoDB.MaxConnectionIdleSecs", 0),
                config.getIntegerPropertyValue("MongoDB.MaxConnectionLifeSecs", 0)
        );
        this.mongoDatabase = MongoClientProvider.createMongoDatabase(mongoClient, config.getStringPropertyValue("MongoDB.Database"));
        this.idGenerator = new TimebasedIdGenerator(
                config.getIntegerPropertyValue("TimebasedId.Context", 0),
                useIDGeneratorLock ? config.getStringPropertyValue("TimebasedId.LockFile", null) : null
        );
        this.skipIndexCreation = config.getBooleanPropertyValue("MongoDB.SkipIndexCreation", false);
    }

    public <T extends AbstractSessionEntity> MatchSessionDao<T>
    getMatchParametersDao() {
        return new MatchSessionMongoDao<>(mongoDatabase, idGenerator, skipIndexCreation);
    }

    public <R extends CDMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> NeuronMatchesDao<R>
    getCDMatchesDao() {
        return new CDMatchesMongoDao<>(mongoDatabase, idGenerator, skipIndexCreation);
    }

    public <R extends PPPMatchEntity<? extends AbstractNeuronEntity, ? extends AbstractNeuronEntity>> NeuronMatchesDao<R>
    getPPPMatchesDao() {
        return new PPPMatchesMongoDao<>(mongoDatabase, idGenerator, skipIndexCreation);
    }

    public <N extends AbstractNeuronEntity> NeuronMetadataDao<N>
    getNeuronMetadataDao() {
        return new NeuronMetadataMongoDao<>(mongoDatabase, idGenerator, skipIndexCreation);
    }

    public PublishedLMImageDao getPublishedImageDao() {
        return new PublishedLMImageMongoDao(mongoDatabase, idGenerator, skipIndexCreation);
    }

    public PublishedURLsDao<NeuronPublishedURLs> getNeuronPublishedUrlsDao() {
        return new PublishedURLsMongoDao(mongoDatabase, idGenerator, skipIndexCreation);
    }

    public PublishedURLsDao<PPPmURLs> getPPPmUrlsDao() {
        return new PPPmURLsMongoDao(mongoDatabase, idGenerator, skipIndexCreation);
    }

}
