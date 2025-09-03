package com.norconex.crawler.core.cluster;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core2.util.SerialUtil;

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
public final class SerializedEnvelope {
    private String className;
    private String serialized;

    public SerializedEnvelope(Object object) {
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
    public static SerializedEnvelope wrap(Object object) {
        return new SerializedEnvelope(object);
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
