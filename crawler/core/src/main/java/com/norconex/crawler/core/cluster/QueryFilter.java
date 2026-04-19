/* Copyright 2025-2026 Norconex Inc.
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

import java.io.Serializable;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Very simple cache entry expression. Allows filtering on a cache value
 * property name and property value.
 *
 * <p><strong>Comparison contract:</strong> all implementations must evaluate
 * field values by comparing {@code Objects.toString(storedValue)} against
 * {@code Objects.toString(filter.getFieldValue())}. This means enums,
 * integers, and other non-String types are compared via their
 * {@code toString()} representation. Callers should pass field values whose
 * {@code toString()} output matches what the stored object produces.
 * For example, use {@code ProcessingStatus.QUEUED.name()} ("{@code QUEUED}")
 * rather than the enum constant itself, unless the enum’s name() is known
 * to match.
 * </p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Data
public class QueryFilter implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String fieldName;
    private final Serializable fieldValue;

    /**
     * Creation method to return all entries.
     * @return entry expression
     */
    public static QueryFilter ofAll() {
        return new QueryFilter(null, null);
    }

    /**
     * Creation method.
     * @param fieldName cache entry property name
     * @param fieldValue cache entry property value
     * @return entry expression
     */
    public static QueryFilter of(String fieldName, Serializable fieldValue) {
        return new QueryFilter(fieldName, fieldValue);
    }
}
