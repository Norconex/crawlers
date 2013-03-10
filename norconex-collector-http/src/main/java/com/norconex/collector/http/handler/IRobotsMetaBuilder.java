package com.norconex.collector.http.handler;

import java.io.Serializable;

import com.norconex.commons.lang.meta.Metadata;

public interface IRobotsMetaBuilder extends Serializable {

    boolean isAllowedFromMetadata(Metadata metadata);
    
}
