/* Copyright 2021-2022 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.crawler.core.store.impl.mongodb;

import static com.norconex.crawler.core.store.impl.mongodb.MongoDataStore.idFilter;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.pojo.PojoCodecProvider;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.text.StringUtil;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.crawler.core.store.DataStoreException;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Data store engine using MongoDB for storing crawl data.
 * </p>
 *
 * {@nx.xml.usage
 * <dataStoreEngine class="MongoDataStoreEngine" />
 *   <connectionString>(MongoDB connection string.)</connectionString>
 * </dataStoreEngine>
 * }
 *
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class MongoDataStoreEngine
        implements DataStoreEngine, Configurable<MongoDataStoreEngineConfig> {

    private static final String STORE_TYPES_KEY =
            MongoDataStoreEngine.class.getSimpleName() + "--storetypes";

    // Non-configurable:
    private MongoClient client;
    private MongoDatabase database;
    private MongoCollection<Document> storeTypes;

    @Getter
    private MongoDataStoreEngineConfig configuration =
            new MongoDataStoreEngineConfig();

    @Override
    public void init(Crawler crawler) {
        LOG.info("Initializing MongoDB data store engine...");
        // create a clean db name
        var dbName = crawler.getId();
        dbName = FileUtil.toSafeFileName(dbName);
        dbName = StringUtil.truncateWithHash(dbName, 63);

        client = MongoClients.create(MongoClientSettings.builder()
                .applicationName(dbName)
                .applyToSocketSettings(b -> b
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS))
                .codecRegistry(CodecRegistries.fromRegistries(
                    MongoClientSettings.getDefaultCodecRegistry(),
                    CodecRegistries.fromProviders(
                        PojoCodecProvider.builder().automatic(true).build())))
                .applyConnectionString(new ConnectionString(
                        configuration.getConnectionString()))
                .build());

        database = client.getDatabase(dbName);

        storeTypes = database.getCollection(STORE_TYPES_KEY);
        LOG.info("MongoDB data store engine initialized.");
    }

    @Override
    public boolean clean() {
        var hasCollections = database.listCollectionNames().first() != null;
        database.drop();
        return hasCollections;
    }

    @Override
    public void close() {
        if (client != null) {
            LOG.info("Closing MongoDB data store engine...");
            client.close();
        }
        client = null;
        database = null;
        LOG.info("MongoDB data store engine closed.");
    }

    @Override
    public <T> DataStore<T> openStore(String name, Class<? extends T> type) {
        storeTypes.replaceOne(idFilter(name), new Document().append(
                "id", name).append("type", type.getName()));
        return new MongoDataStore<>(database, name, type);
    }

    @Override
    public boolean dropStore(String name) {
        if (colExists(name)) {
            database.getCollection(name).drop();
            storeTypes.deleteOne(idFilter(name));
            return true;
        }
        return false;
    }

    @Override
    public boolean renameStore(DataStore<?> dataStore, String newName) {
        var targetExists = colExists(newName);
        MongoDataStore<?> mongoStore = (MongoDataStore<?>) dataStore;
        var oldName = mongoStore.rename(database.getName(), newName);
        storeTypes.replaceOne(idFilter(oldName), new Document()
                .append("id", newName)
                .append("type", mongoStore.getType().getName()));
        return targetExists;
    }

    @Override
    public Set<String> getStoreNames() {
        return new HashSet<>(IteratorUtils.toList(
                database.listCollectionNames().iterator()));
    }

    @Override
    public Optional<Class<?>> getStoreType(String name) {
        if (name == null) {
            return Optional.empty();
        }
        var type = (String) storeTypes.find(
                idFilter(name)).first().get(name);
        if (type == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(ClassUtils.getClass(type));
        } catch (ClassNotFoundException e) {
            throw new DataStoreException(
                    "Could not determine type of: " + name, e);
        }
    }

    private boolean colExists(String name) {
        for (String colName : database.listCollectionNames()) {
            if (StringUtils.equalsIgnoreCase(name, colName)) {
                return true;
            }
        }
        return false;
    }
}
