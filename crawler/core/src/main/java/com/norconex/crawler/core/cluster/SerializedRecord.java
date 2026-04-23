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

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Backend-agnostic envelope for storing arbitrary objects as a pair of:
 * - className: fully qualified class name of the original object
 * - serialized: JSON representation of the original object
 *
 * This type intentionally has no backend-specific annotations so it can be
 * used in any storage path (e.g., String caches, DB) and adapted by
 * backend-specific wrappers (e.g., Protostream-annotated classes).
 */
@Data
@NoArgsConstructor
public final class SerializedRecord {
    private String className;
    private String serialized;

    public SerializedRecord(Object object) {
        if (object != null) {
            this.className = object.getClass().getName();
            this.serialized = SerialUtil.toJsonString(object);
        }
    }

    /**
     * Creates an envelope for the given object.
     * @param object object to wrap
     * @return serialized envelope
     */
    public static SerializedRecord wrap(Object object) {
        return new SerializedRecord(object);
    }

    /**
     * Reconstructs the original object instance using the stored className and
     * JSON payload. Returns null if the payload is null/empty.
     * Throws CrawlerException if the class cannot be resolved.
     * @param <T> the type of the returned object
     * @return the deserialized object
     */
    @SuppressWarnings("unchecked")
    public <T> T unwrap() {
        if (serialized == null || serialized.isEmpty()) {
            return null;
        }
        if (className == null || className.isEmpty()) {
            throw new CrawlerException(
                    "Could not deserialize object: className is null or empty: "
                            + this);
        }
        try {
            var cls = Class.forName(className);
            return (T) SerialUtil.fromJson(serialized, cls);
        } catch (ClassNotFoundException e) {
            throw new CrawlerException(
                    "Could not deserialize object. Class not found: "
                            + className,
                    e);
        }
    }
}
