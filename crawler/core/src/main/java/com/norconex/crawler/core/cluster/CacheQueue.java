package com.norconex.crawler.core.cluster;

import java.util.List;

public interface CacheQueue<T> {

    void add(T item);

    T poll();

    List<T> pollBatch(int batchSize);

    int size();

    boolean isEmpty();

    void clear();
}
