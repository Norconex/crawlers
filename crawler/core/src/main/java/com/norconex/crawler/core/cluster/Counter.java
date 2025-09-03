package com.norconex.crawler.core.cluster;

public interface Counter {
    long incrementAndGet();

    long getAndIncrement();

    long decrementAndGet();

    long getAndDecrement();

    long addAndGet(long delta);

    long getAndAdd(long delta);

    void set(long value);

    long get();

    void reset();
}
