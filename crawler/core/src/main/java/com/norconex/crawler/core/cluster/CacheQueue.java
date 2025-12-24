package com.norconex.crawler.core.cluster;

import java.util.List;

public interface CacheQueue<T> {

    void add(T item);

    T poll();

    List<T> pollBatch(int batchSize);

    int size();

    boolean isEmpty();

    void clear();

    /**
     * Returns whether this queue persists data across restarts.
     * @return true if the queue is persistent, false if ephemeral
     */
    boolean isPersistent();

    /**
     * Gets the name of this cache.
     * @return cache name
     */
    String getName();

}