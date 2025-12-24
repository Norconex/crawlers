package com.norconex.crawler.core.cluster;

import java.util.Iterator;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
public final class SerializedCache
        implements Iterable<SerializedCache.SerializedEntry> {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SerializedEntry {
        String key;
        String json;
    }

    private String cacheName;
    private String className;
    /**
     * Whether this cache is persisted by the crawler between runs.
     */
    private boolean persistent;
    @Getter(value = AccessLevel.NONE)
    private Iterator<SerializedEntry> entries;

    /**
     * Lazy-loaded (in batches) cache entries iterator.
     */
    @Override
    public Iterator<SerializedCache.SerializedEntry> iterator() {
        return entries;
    }
}
