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
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import com.hazelcast.collection.QueueStore;

import lombok.extern.slf4j.Slf4j;

/**
 * RocksDB-based QueueStore implementation for Hazelcast queue persistence.
 * This provides durable storage for Hazelcast queues using RocksDB.
 */
@Slf4j
public class RocksDBQueueStore implements QueueStore<Object> {

    private RocksDB db;
    private Options options;
    private String queueName;
    private long nextId = 0;

    static {
        RocksDB.loadLibrary();
    }

    @Override
    public void store(Long key, Object value) {
        try {
            var keyBytes = longToBytes(key);
            var valueBytes = serialize(value);
            db.put(keyBytes, valueBytes);
        } catch (RocksDBException | IOException e) {
            LOG.error("Failed to store item in queue '{}'", queueName, e);
            throw new RuntimeException(
                    "Failed to store item in queue: " + queueName, e);
        }
    }

    @Override
    public void storeAll(Map<Long, Object> map) {
        for (var entry : map.entrySet()) {
            store(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void delete(Long key) {
        try {
            var keyBytes = longToBytes(key);
            db.delete(keyBytes);
        } catch (RocksDBException e) {
            LOG.error("Failed to delete item from queue '{}'", queueName, e);
            throw new RuntimeException(
                    "Failed to delete item from queue: " + queueName, e);
        }
    }

    @Override
    public void deleteAll(Collection<Long> keys) {
        for (var key : keys) {
            delete(key);
        }
    }

    @Override
    public Object load(Long key) {
        try {
            var keyBytes = longToBytes(key);
            var valueBytes = db.get(keyBytes);
            return valueBytes != null ? deserialize(valueBytes) : null;
        } catch (RocksDBException | IOException | ClassNotFoundException e) {
            LOG.error("Failed to load item from queue '{}'", queueName, e);
            throw new RuntimeException(
                    "Failed to load item from queue: " + queueName, e);
        }
    }

    @Override
    public Map<Long, Object> loadAll(Collection<Long> keys) {
        var result = new HashMap<Long, Object>();
        for (var key : keys) {
            var value = load(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    @Override
    public Set<Long> loadAllKeys() {
        var keys = new HashSet<Long>();
        try (var iterator = db.newIterator()) {
            iterator.seekToFirst();
            while (iterator.isValid()) {
                keys.add(bytesToLong(iterator.key()));
                iterator.next();
            }
        }
        return keys;
    }

    public void init(Properties properties, String queueName) {
        this.queueName = queueName;
        var dbDir = properties.getProperty("database.dir");
        if (dbDir == null) {
            throw new IllegalArgumentException(
                    "database.dir property is required");
        }

        try {
            var dbPath = Paths.get(dbDir, "queues", queueName);
            Files.createDirectories(dbPath);

            options = new Options()
                    .setCreateIfMissing(true)
                    .setMaxOpenFiles(100);

            db = RocksDB.open(options, dbPath.toString());

            // Find the highest key to set nextId
            try (var iterator = db.newIterator()) {
                iterator.seekToLast();
                if (iterator.isValid()) {
                    nextId = bytesToLong(iterator.key()) + 1;
                }
            }

            LOG.info("RocksDB QueueStore initialized for queue '{}' at: {}",
                    queueName, dbPath);
        } catch (RocksDBException | IOException e) {
            throw new RuntimeException(
                    "Failed to initialize RocksDB for queue: " + queueName, e);
        }
    }

    public void destroy() {
        if (db != null) {
            db.close();
            LOG.info("RocksDB QueueStore closed for queue: {}", queueName);
        }
        if (options != null) {
            options.close();
        }
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

    private byte[] longToBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    private long bytesToLong(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }
}
