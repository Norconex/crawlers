package com.norconex.collector.http.filter;

import java.io.Serializable;

import com.norconex.commons.lang.meta.Metadata;

/**
 * Filter a document based on their HTTP headers, before the document content
 * is downloaded.
 * <p>
 * It is highly recommended to overwrite the <code>toString()</code> method
 * to representing this filter properly in human-readable form (e.g. logging).
 * It is a good idea to include specifics of this filter so crawler users 
 * can know exactly why documents got accepted/rejected rejected if need be.
 * </p>
 * <p>HTTP Header Filters should be immutable.</p>
 * @author Pascal Essiembre
 */
public interface IHttpHeadersFilter extends Serializable {

    /**
     * Whether to accept a URL HTTP headers.  
     * @param url the URL to accept/reject its headers
     * @param headers HTTP headers associated with the URL
     * @return <code>true</code> if accepted, <code>false</code> otherwise
     */
    boolean acceptHeaders(String url, Metadata headers);
    
}
