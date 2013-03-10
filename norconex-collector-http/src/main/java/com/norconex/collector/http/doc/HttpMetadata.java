package com.norconex.collector.http.doc;

import java.util.Collection;

import com.norconex.commons.lang.meta.Metadata;
import com.norconex.importer.ContentType;

public class HttpMetadata extends Metadata {

	//TODO allow for custom properties? (e.g. JEF ConfigProperties?)
	
	private static final long serialVersionUID = 1454870639551983430L;

//    public static final String HTTP_HEADER_PREFIX = "http.header.";
    public static final String CONNECTOR_PREFIX = "http.connector.";
	
	public static final String HTTP_CONTENT_TYPE = "Content-Type";
    public static final String HTTP_CONTENT_LENGTH = "Content-Length";
    
    public static final String DOC_URL = CONNECTOR_PREFIX + "URL";
    public static final String DOC_MIMETYPE = CONNECTOR_PREFIX + "MIMETYPE";
    public static final String DOC_CHARSET = CONNECTOR_PREFIX + "CHARSET";
    
    public static final String REFERNCED_URLS = 
            CONNECTOR_PREFIX + "referencedURLs";

	
	public HttpMetadata(String documentURL) {
		super(false);
		addPropertyValue(DOC_URL, documentURL);
	}

	public ContentType getContentType() {
	    String type = getPropertyValue(HTTP_CONTENT_TYPE);
	    if (type != null) {
	        type = type.replaceFirst("(.*?)(\\;)(.*)", "$1");
	    }
		return ContentType.newContentType(type);
	}
	public String getDocumentUrl() {
	    return getPropertyValue(DOC_URL);
	}
	public Collection<String> getDocumentUrls() {
	    return getPropertyValues(REFERNCED_URLS);
	}
	
}
