package com.norconex.collector.http.handler;

import java.io.Serializable;

import com.norconex.commons.lang.map.Properties;

public interface IHttpHeadersChecksummer extends Serializable {

	String createChecksum(Properties metadata);
	
}
