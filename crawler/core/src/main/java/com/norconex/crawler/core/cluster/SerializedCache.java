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
    @Getter(value = AccessLevel.NONE)
    private Iterator<SerializedEntry> entries;

    @Override
    public Iterator<SerializedCache.SerializedEntry> iterator() {
        return entries;
    }
}
