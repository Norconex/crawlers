package com.norconex.crawler.core.cluster.impl.infinispan;

import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoField;

import com.norconex.crawler.core.cluster.CacheException;
import com.norconex.crawler.core.cluster.SerializedEnvelope;
import com.norconex.crawler.core2.util.SerialUtil;

import lombok.Data;

@Proto
@Data
public final class StringSerializedObject {

    @ProtoField(1)
    public String className;

    @ProtoField(2)
    public String serialized;

    public StringSerializedObject() {
    }

    public StringSerializedObject(Object object) {
        if (object != null) {
            className = object.getClass().getName();
            serialized = SerialUtil.toJsonString(object);
        }
    }

    /**
     * Creates a Protostream-compatible wrapper from a neutral envelope.
     * @param env serialized envelope
     * @return proto-wrapped object
     */
    public static StringSerializedObject fromEnvelope(SerializedEnvelope env) {
        var sso = new StringSerializedObject();
        if (env != null) {
            sso.className = env.getClassName();
            sso.serialized = env.getSerialized();
        }
        return sso;
    }

    /**
     * Converts this wrapper to a neutral envelope for backend-agnostic use.
     * @return serialized envelope
     */
    public SerializedEnvelope toEnvelope() {
        var env = new SerializedEnvelope();
        env.setClassName(className);
        env.setSerialized(serialized);
        return env;
    }

    @SuppressWarnings("unchecked")
    public <T> T toObject() {
        if (serialized == null || serialized.isEmpty()) {
            return null;
        }
        if (className == null || className.isEmpty()) {
            throw new CacheException(
                    "Could not deserialize object: className is null or empty. "
                            + toString());
        }
        try {
            return (T) SerialUtil.fromJson(
                    serialized, Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new CacheException(
                    "Could not deserialize object: " + toString(), e);
        }
    }
}
