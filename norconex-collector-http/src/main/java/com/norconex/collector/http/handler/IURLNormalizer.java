package com.norconex.collector.http.handler;

import java.io.Serializable;

import com.norconex.collector.http.filter.IURLFilter;

/**
 * Responsible for normalizing URLs.  Normalization is taking a raw URL and
 * modifying it to its most basic or standard form.  In other words, this makes
 * different URLs "equivalent".  This allows to eliminate URL variations
 * that points to the same content (e.g. URL carrying temporary session 
 * information).  This action takes place right after URLs are extracted 
 * from a document, before each of these URLs is even considered
 * for further processing.  Returning null will effectively tells the crawler
 * to not even consider it for processing (it won't go through the regular
 * document processing flow).  You may want to consider {@link IURLFilter} 
 * to exclude URLs as part has the regular document processing flow.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IURLNormalizer extends Serializable {

    /**
     * Normalize the given URL.
     * @param url the URL to normalize
     * @return the normalized URL
     */
    String normalizeURL(String url);
    
}
