package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.util.ArrayList;
import java.util.List;

import com.hazelcast.collection.IQueue;
import com.norconex.crawler.core.cluster.CacheQueue;

public class HazelcastQueueAdapter<T> implements CacheQueue<T> {
    private final IQueue<T> queue;

    public HazelcastQueueAdapter(IQueue<T> queue) {
        this.queue = queue;
    }

    @Override
    public void add(T item) {
        queue.add(item);
    }

    @Override
    public T poll() {
        return queue.poll();
    }

    @Override
    public List<T> pollBatch(int batchSize) {
        List<T> batch = new ArrayList<>(batchSize);
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
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void clear() {
        queue.clear();
    }
}
