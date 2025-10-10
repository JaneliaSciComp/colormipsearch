package org.janelia.colormipsearch.dao.mongo.support;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public class MongoClientProvider {

    public static MongoClient createMongoClient(
            String mongoConnectionURL,
            String mongoServer,
            String mongoAuthDatabase,
            String mongoUsername,
            String mongoPassword,
            String mongoReplicaSet,
            boolean useSSL,
            boolean readFromSecondary,
            int connectionsPerHost,
            int connectTimeoutInMillis,
            int maxConnecting,
            int maxWaitTimeInSecs,
            int maxConnectionIdleTimeInSecs,
            int maxConnLifeTimeInSecs) {
        return createMongoClient(
                MongoSettingsProvider.prepareMongoSettings(
                        mongoConnectionURL, mongoServer, mongoAuthDatabase, mongoUsername, mongoPassword, mongoReplicaSet,
                        useSSL, readFromSecondary,
                        connectionsPerHost, connectTimeoutInMillis, maxConnecting,
                        maxWaitTimeInSecs, maxConnectionIdleTimeInSecs, maxConnLifeTimeInSecs
                )
        );
    }

    public static MongoClient createMongoClient(MongoClientSettings clientSettings) {
        return MongoClients.create(clientSettings);
    }

    public static MongoDatabase createMongoDatabase(MongoClient mongoClient, String mongoDatabaseName) {
        return mongoClient.getDatabase(mongoDatabaseName);
    }

}
