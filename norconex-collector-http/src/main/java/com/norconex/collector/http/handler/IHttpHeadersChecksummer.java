package com.norconex.collector.http.handler;

import java.io.Serializable;

import com.norconex.commons.lang.meta.Metadata;

public interface IHttpHeadersChecksummer extends Serializable {

	String createChecksum(Metadata metadata);
	
}
