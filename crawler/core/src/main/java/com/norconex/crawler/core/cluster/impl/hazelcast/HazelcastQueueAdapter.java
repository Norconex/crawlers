package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.util.ArrayList;
import java.util.List;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.CacheQueue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HazelcastQueueAdapter<T> implements CacheQueue<T> {
    private final IQueue<T> queue;
    private final HazelcastInstance hazelcastInstance;

    public HazelcastQueueAdapter(IQueue<T> queue,
            HazelcastInstance hazelcastInstance) {
        this.queue = queue;
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public void add(T item) {
        if (!isQueueAvailable()) {
            return;
        }
        queue.add(item);
    }

    @Override
    public T poll() {
        if (!isQueueAvailable()) {
            return null;
        }
        return queue.poll();
    }

    @Override
    public List<T> pollBatch(int batchSize) {
        var batch = new ArrayList<T>(batchSize);
        if (!isQueueAvailable()) {
            return batch;
        }
        for (var i = 0; i < batchSize; i++) {
            var item = queue.poll();
            if (item == null) {
                break;
            }
            batch.add(item);
        }
        return batch;
    }

    @Override
    public int size() {
        if (!isQueueAvailable()) {
            return 0;
        }
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        if (!isQueueAvailable()) {
            return true;
        }
        return queue.isEmpty();
    }

    @Override
    public void clear() {
        if (!isQueueAvailable()) {
            return;
        }
        queue.clear();
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
