package com.norconex.crawler.core.cluster.impl.hazelcast;

import com.hazelcast.collection.IQueue;
import com.norconex.crawler.core.cluster.CacheQueue;
import java.util.List;
import java.util.stream.Collectors;

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
        return queue.stream().limit(batchSize).collect(Collectors.toList());
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
