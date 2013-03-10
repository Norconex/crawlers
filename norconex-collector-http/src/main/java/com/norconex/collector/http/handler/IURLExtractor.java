package com.norconex.collector.http.handler;

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.util.Set;

import com.norconex.importer.ContentType;

/**
 * Responsible for extracting URLs out of a document.
 * @author Pascal Essiembre
 */
public interface IURLExtractor extends Serializable  {

	/**
	 * Extracts URLs out of a document.
	 * @param document the document
	 * @param documentUrl document url
	 * @param contentType the document content type
	 * @return a set of URLs
	 * @throws IOException problem reading the document
	 */
    Set<String> extractURLs(
            Reader document, String documentUrl, ContentType contentType)
            throws IOException;
    
}
