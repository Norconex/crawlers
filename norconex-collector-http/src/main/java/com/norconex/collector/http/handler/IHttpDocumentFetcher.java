package com.norconex.collector.http.handler;

import java.io.Serializable;

import org.apache.commons.httpclient.HttpClient;

import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.doc.HttpDocument;

/**
 * Fetches the HTTP document and its metadata (HTTP Headers).  The 
 * document metadata is populated with the HTTP Headers and the document body
 * is saved to the document local file.
 * @author Pascal Essiembre
 */
public interface IHttpDocumentFetcher extends Serializable {

	/**
	 * Fetches HTTP document and saves it to a local file
	 * @param httpClient the HTTP client
	 * @param doc HttpDocument the document to fetch and save
	 * @return URL status
	 */
	CrawlStatus fetchDocument(
			HttpClient httpClient, HttpDocument doc);
	
}
