package com.norconex.collector.http.handler;

import java.io.Serializable;

import org.apache.commons.httpclient.HttpClient;

import com.norconex.commons.lang.map.Properties;

/**
 * Fetches the HTTP Header, typically via a HEAD request.
 * @author Pascal Essiembre
 */
public interface IHttpHeadersFetcher extends Serializable {

    /**
     * Returning <code>null</code> means the headers could not be fetched
     * and the associated document will be skipped (treated as rejected).
     * @param httpClient the HTTP Client
     * @param url the url from which to fetch the headers
     * @return  HTTP headers as metadata
     */
    Properties fetchHTTPHeaders(HttpClient httpClient, String url);
	
}
