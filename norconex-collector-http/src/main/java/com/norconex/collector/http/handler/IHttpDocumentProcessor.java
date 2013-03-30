package com.norconex.collector.http.handler;

import java.io.Serializable;

import org.apache.commons.httpclient.HttpClient;

import com.norconex.collector.http.doc.HttpDocument;

/**
 * Custom processing (optional) performed on a document.  Can be used 
 * just before of just after a document has been imported.  This is to
 * perform processing on the raw document.  To perform processing on
 * its extracted content, see the Importer for that.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IHttpDocumentProcessor extends Serializable {

	/**
	 * Processes a document.
	 * @param httpClient HTTP Client
	 * @param doc the document
	 */
    void processDocument(HttpClient httpClient, HttpDocument doc);
}
