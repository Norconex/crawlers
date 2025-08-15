package com.norconex.crawler.core2.cluster;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Basic per-node statistics for a continuous task.
 * (Extended to include queued and processing snapshot counts.)
 */
public class ContinuousStats {
    private final AtomicLong processed = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong noWorkCycles = new AtomicLong();
    private final AtomicLong queued = new AtomicLong();
    private final AtomicLong processing = new AtomicLong();
    private volatile long lastActivityEpochMs = System.currentTimeMillis();

    public void incProcessed() {
        processed.incrementAndGet();
        touch();
    }

    public void incFailed() {
        failed.incrementAndGet();
        touch();
    }

    public void incNoWork() {
        noWorkCycles.incrementAndGet();
    }

    public void touch() {
        lastActivityEpochMs = System.currentTimeMillis();
    }

    public void setQueued(long q) {
        queued.set(q);
    }

    public void setProcessing(long p) {
        processing.set(p);
    }

    public long getProcessed() {
        return processed.get();
    }

    public long getFailed() {
        return failed.get();
    }

    public long getNoWorkCycles() {
        return noWorkCycles.get();
    }

    public long getQueued() {
        return queued.get();
    }

    public long getProcessing() {
        return processing.get();
    }

    public long getLastActivityEpochMs() {
        return lastActivityEpochMs;
    }

    public Instant getLastActivityTime() {
        return Instant.ofEpochMilli(lastActivityEpochMs);
    }
}
