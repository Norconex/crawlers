/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A generic Hazelcast Compact serializer that delegates to Jackson
 * (via {@link SerialUtil}) for the actual serialization. The object graph
 * is stored as a single JSON string field inside the Compact frame.
 * <p>
 * This avoids the overhead of Java {@code Serializable} class descriptors
 * while reusing the already-tested Jackson mapping for complex types such
 * as {@link com.norconex.crawler.core.ledger.CrawlEntry} and its
 * subclasses.
 * </p>
 *
 * @param <T> the value type
 */
@RequiredArgsConstructor
public class JacksonCompactSerializer<T> implements CompactSerializer<T> {

    private final Class<T> type;

    @Override
    public @NonNull T read(@NonNull CompactReader reader) {
        var json = reader.readString("json");
        return SerialUtil.fromJson(json, type);
    }

    @Override
    public void write(@NonNull CompactWriter writer, @NonNull T value) {
        writer.writeString("json", SerialUtil.toJsonString(value));
    }

    @Override
    public @NonNull String getTypeName() {
        return type.getName();
    }

    @Override
    public @NonNull Class<T> getCompactClass() {
        return type;
    }
}
