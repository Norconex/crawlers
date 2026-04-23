/* Copyright 2026 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    public enum CacheType {
        MAP, QUEUE
    }

    private String cacheName;
    private String className;
    /**
     * Whether this cache is persisted by the crawler between runs.
     */
    private boolean persistent;
    private CacheType cacheType;
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
