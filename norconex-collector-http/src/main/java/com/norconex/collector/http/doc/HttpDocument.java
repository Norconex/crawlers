package com.norconex.collector.http.doc;

import java.io.File;
import java.io.Serializable;

public class HttpDocument implements Serializable {

	private static final long serialVersionUID = 4376740210800410675L;
	private final String url;
	private final File localFile;
	private final HttpMetadata metadata;

	public HttpDocument(String url, File localFile) {
		super();
		this.url = url;
		this.localFile = localFile;
		this.metadata = new HttpMetadata(url);
	}

	public String getUrl() {
	    //TODO make it point to meta URL or keep separate to distinguish
	    //between original URL and potentiallly overwritten one?
		return url;
	}

	public File getLocalFile() {
		return localFile;
	}

	public HttpMetadata getMetadata() {
		return metadata;
	}
}
