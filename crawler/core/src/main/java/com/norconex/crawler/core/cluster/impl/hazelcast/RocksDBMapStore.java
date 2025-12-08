/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapLoaderLifecycleSupport;
import com.hazelcast.map.MapStore;

import lombok.extern.slf4j.Slf4j;

/**
 * RocksDB-based MapStore implementation for Hazelcast persistence.
 * This provides durable storage for Hazelcast maps using RocksDB.
 * Supports any serializable key and value types.
 *
 * <p>Implements backup persistence by tracking all MapStore instances
 * and providing a method to persist all local entries (including backups)
 * before shutdown.</p>
 */
@Slf4j
public class RocksDBMapStore
        implements MapStore<Object, Object>,
        MapLoaderLifecycleSupport {

    // Track all instances so we can persist backup data before shutdown
    private static final java.util.Map<String, RocksDBMapStore> INSTANCES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private RocksDB db;
    private Options options;
    private String mapName;
    private HazelcastInstance hazelcastInstance;

    /**
     * Persists all backup data across all RocksDBMapStore instances.
     * Should be called BEFORE Hazelcast shutdown begins.
     */
    public static void persistAllBackupData() {
        LOG.info("Persisting backup data for {} map stores", INSTANCES.size());
        for (var entry : INSTANCES.entrySet()) {
            LOG.debug("Persisting backup data for map: {}", entry.getKey());
            entry.getValue().persistAllLocalEntries();
        }
    }

    static {
        RocksDB.loadLibrary();
    }

    @Override
    public void init(
            com.hazelcast.core.HazelcastInstance hazelcastInstance,
            Properties properties, String mapName) {
        this.hazelcastInstance = hazelcastInstance;
        this.mapName = mapName;
        var dbDir = properties.getProperty("database.dir");
        if (dbDir == null) {
            throw new IllegalArgumentException(
                    "database.dir property is required");
        }

        try {
            var dbPath = Paths.get(dbDir, mapName);

            LOG.debug("RocksDB map store path: {}", dbPath.toString());

            Files.createDirectories(dbPath);

            options = new Options()
                    .setCreateIfMissing(true)
                    .setMaxOpenFiles(100);

            System.err.println("XXX [%s] RocksDB opening path: %s".formatted(
                    Thread.currentThread().getName(),
                    dbPath.toString()));

            db = RocksDB.open(options, dbPath.toString());

            // Register this instance for backup persistence
            INSTANCES.put(mapName, this);

            LOG.info("RocksDB MapStore initialized for map '{}' at: {}",
                    mapName, dbPath);
        } catch (RocksDBException | IOException e) {
            throw new RuntimeException(
                    "Failed to initialize RocksDB for map: " + mapName, e);
        }
    }

    /**
     * Persists all local entries (primary + backup) to RocksDB.
     * This should be called BEFORE Hazelcast shutdown begins.
     */
    void persistAllLocalEntries() {
        if (hazelcastInstance == null || db == null) {
            LOG.debug(
                    "Skipping persistAllLocalEntries for {} (null instance or db)",
                    mapName);
            return;
        }

        try {
            var map = hazelcastInstance.getMap(mapName);
            var localKeySetSize = map.localKeySet().size();
            LOG.info(
                    "Persisting all entries for map '{}' before shutdown (including backups, {} local keys)...",
                    mapName, localKeySetSize);

            // Get ALL local entries (primary + backup)
            var localEntries = map.getAll(map.localKeySet());
            LOG.debug("Found {} local entries (primary + backup) for map '{}'",
                    localEntries.size(), mapName);

            // Persist all of them to RocksDB
            var persisted = 0;
            for (var entry : localEntries.entrySet()) {
                try {
                    var keyBytes = serialize(entry.getKey());
                    var valueBytes = serialize(entry.getValue());
                    db.put(keyBytes, valueBytes);
                    persisted++;
                } catch (Exception e) {
                    LOG.warn("Failed to persist entry during shutdown: {}",
                            entry.getKey(), e);
                }
            }

            LOG.info("Persisted {} entries for map '{}' during shutdown",
                    persisted, mapName);
        } catch (Exception e) {
            LOG.error("Failed to persist map data for '{}' during shutdown",
                    mapName, e);
        }
    }

    @Override
    public void destroy() {
        // Unregister this instance
        INSTANCES.remove(mapName);

        if (db != null) {
            db.close();
            LOG.info("RocksDB MapStore closed for map: {}", mapName);
        }
        if (options != null) {
            options.close();
        }
    }

    @Override
    public void store(Object key, Object value) {
        try {
            var keyBytes = serialize(key);
            var valueBytes = serialize(value);
            db.put(keyBytes, valueBytes);
        } catch (RocksDBException | IOException e) {
            LOG.error("Failed to store key '{}' in RocksDB", key, e);
            throw new RuntimeException("Failed to store key: " + key, e);
        }
    }

    @Override
    public void storeAll(Map<Object, Object> map) {
        for (var entry : map.entrySet()) {
            store(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void delete(Object key) {
        try {
            var keyBytes = serialize(key);
            db.delete(keyBytes);
        } catch (RocksDBException | IOException e) {
            LOG.error("Failed to delete key '{}' from RocksDB", key, e);
            throw new RuntimeException("Failed to delete key: " + key, e);
        }
    }

    @Override
    public void deleteAll(Collection<Object> keys) {
        for (var key : keys) {
            delete(key);
        }
    }

    @Override
    public Object load(Object key) {
        try {
            var keyBytes = serialize(key);
            var valueBytes = db.get(keyBytes);
            return valueBytes != null ? deserialize(valueBytes) : null;
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOG.error("Failed to load key '{}' from RocksDB", key, e);
            throw new RuntimeException("Failed to load key: " + key, e);
        }
    }

    @Override
    public Map<Object, Object> loadAll(Collection<Object> keys) {
        var result = new HashMap<>();
        for (var key : keys) {
            var value = load(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    @Override
    public Iterable<Object> loadAllKeys() {
        var keys = new ArrayList<>();
        try (var iterator = db.newIterator()) {
            iterator.seekToFirst();
            while (iterator.isValid()) {
                keys.add(deserialize(iterator.key()));
                iterator.next();
            }
        } catch (IOException | ClassNotFoundException e) {
            LOG.error("Failed to load all keys from RocksDB", e);
            throw new RuntimeException("Failed to load all keys", e);
        }
        return keys;
    }

    /**
     * Closes all RocksDB instances. Should be called during
     * application shutdown or between tests.
     */
    public static void closeAll() {
        LOG.info("Closing all RocksDB MapStore instances");
        for (var entry : INSTANCES.entrySet()) {
            try {
                var store = entry.getValue();
                if (store.db != null) {
                    store.db.close();
                    LOG.info("Closed RocksDB for map: {}", entry.getKey());
                }
                if (store.options != null) {
                    store.options.close();
                }
            } catch (Exception e) {
                LOG.warn("Failed to close RocksDB for map: {}",
                        entry.getKey(), e);
            }
        }
        INSTANCES.clear();
    }

    private byte[] serialize(Object obj) throws IOException {
        try (var bos = new ByteArrayOutputStream();
                var oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        }
    }

    private Object deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (var bis = new ByteArrayInputStream(bytes);
                var ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        }
    }
}
