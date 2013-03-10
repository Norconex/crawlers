package com.norconex.collector.http.filter;

import java.io.Serializable;

/**
 * Filter a document based on its URL, before any download of HTTP headers
 * or content of a document.
 * <p>
 * It is highly recommended to overwrite the <code>toString()</code> method
 * to representing this filter properly in human-readable form (e.g. logging).
 * It is a good idea to include specifics of this filter so crawler users 
 * can know exactly why documents got accepted/rejected rejected if need be.
 * </p>
 * <p>URL Filters should be immutable.</p>
 * @author Pascal Essiembre
 */
public interface IURLFilter extends Serializable {

    /**
     * Whether to accept this URL.  
     * @param url the URL to accept/reject
     * @return <code>true</code> if accepted, <code>false</code> otherwise
     */
    boolean acceptURL(String url);
    
}
