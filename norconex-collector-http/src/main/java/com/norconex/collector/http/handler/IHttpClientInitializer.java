package com.norconex.collector.http.handler;

import java.io.Serializable;

import org.apache.commons.httpclient.HttpClient;


public interface IHttpClientInitializer extends Serializable  {

	void initializeHTTPClient(HttpClient httpClient);
}
