package com.norconex.collector.http.handler;

import java.io.Serializable;

import com.norconex.collector.http.doc.HttpDocument;

public interface IHttpDocumentChecksummer extends Serializable {

	String createChecksum(HttpDocument document);
	
}
