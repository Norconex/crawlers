package com.norconex.crawler.core2.cluster.impl.infinispan;

import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoField;

import com.norconex.crawler.core2.cluster.CacheException;
import com.norconex.crawler.core2.util.SerialUtil;

import lombok.Data;
import lombok.NoArgsConstructor;

@Proto
@Data
@NoArgsConstructor
public final class StringSerializedObject {

    @ProtoField(1)
    public String className;

    @ProtoField(2)
    public String serialized;

    public StringSerializedObject(Object object) {
        if (object != null) {
            className = object.getClass().getName();
            serialized = SerialUtil.toJsonString(object);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T toObject() {
        if (serialized == null) {
            return null;
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
