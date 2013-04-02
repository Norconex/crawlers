package com.norconex.collector.http.handler;

import java.io.Serializable;

import org.apache.commons.httpclient.HttpClient;

/**
 * 
 * 
 * Implementors also implementing IXMLConfigurable must name their XML tag
 * <code>httpClientInitializer</code> to ensure it gets loaded properly.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 *
 */
public interface IHttpClientInitializer extends Serializable  {

	void initializeHTTPClient(HttpClient httpClient);
}
