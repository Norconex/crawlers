package com.norconex.crawler.core2.cluster.impl.infinispan;

import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoField;

import lombok.Data;

@Proto
@Data
public final class FallbackSerializedObject {

    @ProtoField(1)
    public String className;

    @ProtoField(2)
    public String serialized;
}
