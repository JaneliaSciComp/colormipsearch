package org.janelia.colormipsearch.dao.mongo.support;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import org.apache.commons.lang3.StringUtils;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.janelia.colormipsearch.model.AbstractBaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoDBHelper {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBHelper.class);

    public static MongoClient createMongoClient(
            String mongoConnectionURL,
            String mongoServer,
            String mongoAuthDatabase,
            String mongoUsername,
            String mongoPassword,
            String mongoReplicaSet,
            boolean useSSL,
            int connectionsPerHost,
            int connectTimeoutInMillis,
            int maxConnecting,
            int maxWaitTimeInSecs,
            int maxConnectionIdleTimeInSecs,
            int maxConnLifeTimeInSecs) {
        CodecRegistry codecRegistry = RegistryHelper.createCodecRegistry();
        MongoClientSettings.Builder mongoClientSettingsBuilder = MongoClientSettings.builder()
                .codecRegistry(CodecRegistries.fromRegistries(
                        MongoClientSettings.getDefaultCodecRegistry(),
                        codecRegistry))
                .writeConcern(WriteConcern.JOURNALED)
                .readPreference(ReadPreference.primaryPreferred())
                .retryWrites(true)
                .applyToConnectionPoolSettings(builder -> {
                    if (connectionsPerHost > 0) {
                        builder.maxSize(connectionsPerHost);
                    }
                    if (maxWaitTimeInSecs > 0) {
                        builder.maxWaitTime(maxWaitTimeInSecs, TimeUnit.SECONDS);
                    }
                    if (maxConnectionIdleTimeInSecs > 0) {
                        builder.maxConnectionIdleTime(maxConnectionIdleTimeInSecs, TimeUnit.SECONDS);
                    }
                    if (maxConnLifeTimeInSecs > 0) {
                        builder.maxConnectionLifeTime(maxConnLifeTimeInSecs, TimeUnit.SECONDS);
                    }
                    if (maxConnecting > 0) {
                        builder.maxConnecting(maxConnecting);
                    }
                })
                .applyToSocketSettings(builder -> {
                    if (connectTimeoutInMillis > 0) {
                        builder.connectTimeout(connectTimeoutInMillis, TimeUnit.MILLISECONDS);
                    }
                })
                .applyToSslSettings(builder -> builder.enabled(useSSL));
        if (StringUtils.isNotBlank(mongoServer)) {
            List<ServerAddress> clusterMembers = Arrays.stream(StringUtils.split(mongoServer, ','))
                    .filter(StringUtils::isNotBlank)
                    .map(ServerAddress::new)
                    .collect(Collectors.toList());
            LOG.info("Connect to {}", clusterMembers);
            mongoClientSettingsBuilder.applyToClusterSettings(builder -> builder.hosts(clusterMembers));
        } else {
            // use connection URL
            if (StringUtils.isBlank(mongoConnectionURL)) {
                LOG.error("Neither mongo server(s) nor the mongo URL have been specified");
                throw new IllegalStateException("Neither mongo server(s) nor the mongo URL have been specified");
            } else {
                mongoClientSettingsBuilder.applyConnectionString(new ConnectionString(mongoConnectionURL));
            }
        }
        if (StringUtils.isNotBlank(mongoReplicaSet)) {
            LOG.info("Use replica set: {}", mongoReplicaSet);
            mongoClientSettingsBuilder.applyToClusterSettings(builder -> builder.requiredReplicaSetName(mongoReplicaSet));
        }
        if (StringUtils.isNotBlank(mongoUsername)) {
            LOG.info("Authenticate to MongoDB ({}@{}){}", mongoAuthDatabase, StringUtils.defaultIfBlank(mongoServer, mongoConnectionURL),
                    StringUtils.isBlank(mongoUsername) ? "" : " as user " + mongoUsername);
            char[] passwordChars = StringUtils.isBlank(mongoPassword) ? null : mongoPassword.toCharArray();
            mongoClientSettingsBuilder.credential(MongoCredential.createCredential(mongoUsername, mongoAuthDatabase, passwordChars));
        }
        return MongoClients.create(mongoClientSettingsBuilder.build());
    }

    public static MongoDatabase createMongoDatabase(MongoClient mongoClient, String mongoDatabaseName) {
        return mongoClient.getDatabase(mongoDatabaseName);
    }

    static ObjectMapper createMongoObjectMapper() {
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.addFilter(MongoIgnoredFieldFilter.FILTER_NAME, new MongoIgnoredFieldFilter());
        return new ObjectMapper()
                .registerModule(new MongoModule())
                .addMixIn(AbstractBaseEntity.class, ApplyMongoIgnoreFilterMixIn.class)
                .setFilterProvider(filterProvider)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                ;
    }

}
