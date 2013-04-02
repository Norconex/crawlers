package com.norconex.collector.http.filter;

import java.io.Serializable;

import com.norconex.collector.http.doc.HttpDocument;

/**
 * Filter a document after the document content is downloaded.
 * <p>
 * It is highly recommended to overwrite the <code>toString()</code> method
 * to representing this filter properly in human-readable form (e.g. logging).
 * It is a good idea to include specifics of this filter so crawler users 
 * can know exactly why documents got accepted/rejected rejected if need be.
 * </p>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IHttpDocumentFilter extends Serializable {

    /**
     * Whether to accept a HTTP document.  
     * @param document the document to validate
     * @return <code>true</code> if accepted, <code>false</code> otherwise
     */
    boolean acceptDocument(HttpDocument document);
    
}
