package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.SerializationException;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.CacheQueue;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HazelcastQueueAdapter<T> implements CacheQueue<T> {

    private final IQueue<Object> queue;
    private final HazelcastInstance hazelcastInstance;
    private final Class<T> valueType;

    public HazelcastQueueAdapter(IQueue<Object> queue,
            HazelcastInstance hazelcastInstance, Class<T> valueType) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.hazelcastInstance = hazelcastInstance;
        this.valueType =
                valueType == null ? (Class<T>) String.class : valueType;
    }

    @Override
    public void add(T item) {
        if (!isQueueAvailable() || item == null) {
            return;
        }
        Object toStore;
        // If the value is already a String, store as-is. Otherwise
        // serialize to JSON for maximum JDBC portability.
        if (item instanceof String || valueType == String.class) {
            toStore = item;
        } else {
            try {
                toStore = SerialUtil.toJsonString(item);
            } catch (SerializationException e) {
                LOG.debug(
                        "Could not serialize queue item; storing toString: {}",
                        e.toString());
                toStore = item.toString();
            }
        }
        // Offer to the distributed FIFO queue.
        try {
            queue.offer(toStore);
        } catch (Exception e) {
            LOG.debug("Could not add item to queue '{}': {}",
                    queue.getName(), e.toString());
        }
    }

    @Override
    public T poll() {
        var list = pollBatch(1);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<T> pollBatch(int batchSize) {
        var batch = new ArrayList<T>(Math.max(1, batchSize));
        if (!isQueueAvailable()) {
            return batch;
        }

        for (int i = 0; i < batchSize; i++) {
            Object obj = null;
            try {
                obj = queue.poll();
            } catch (Exception e) {
                LOG.debug("Could not poll item from queue '{}': {}",
                        queue.getName(), e.toString());
            }
            if (obj == null) {
                break; // queue empty
            }

            T val = null;
            if (obj instanceof String str && valueType != String.class) {
                try {
                    val = SerialUtil.fromJson(str, valueType);
                } catch (Exception e) {
                    LOG.debug("Could not deserialize queue item: {}",
                            e.toString());
                    // fallback to returning the raw string
                    @SuppressWarnings("unchecked")
                    T cast = (T) str;
                    val = cast;
                }
            } else {
                @SuppressWarnings("unchecked")
                T cast = (T) obj;
                val = cast;
            }
            batch.add(val);
        }

        return batch;
    }

    @Override
    public int size() {
        if (!isQueueAvailable()) {
            return 0;
        }
        try {
            return queue.size();
        } catch (Exception e) {
            LOG.debug("Could not get size for queue '{}': {}",
                    queue.getName(), e.toString());
            return 0;
        }
    }

    @Override
    public boolean isEmpty() {
        if (!isQueueAvailable()) {
            return true;
        }
        try {
            return queue.isEmpty();
        } catch (Exception e) {
            LOG.debug("Could not check emptiness for queue '{}': {}",
                    queue.getName(), e.toString());
            return true;
        }
    }

    @Override
    public void clear() {
        if (!isQueueAvailable()) {
            return;
        }
        try {
            queue.clear();
        } catch (Exception e) {
            LOG.debug("Could not clear queue '{}': {}",
                    queue.getName(), e.toString());
        }
    }

    private boolean isQueueAvailable() {
        var lifecycle = hazelcastInstance.getLifecycleService();
        if (!lifecycle.isRunning()) {
            var name = queue.getName();
            LOG.debug("Skipping operation on queue '{}' because "
                    + "Hazelcast instance is not running.", name);
            return false;
        }
        return true;
    }
}
