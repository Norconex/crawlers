/**
 * 
 */
package com.norconex.collector.http.crawler;

import com.norconex.collector.core.crawler.event.DocCrawlEvent;
import com.norconex.collector.core.doccrawl.IDocCrawl;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpDocCrawlEvent extends DocCrawlEvent {

    public static final String SAVED_FILE = "SAVED_FILE";
    public static final String CREATED_ROBOTS_META = "CREATED_ROBOTS_META";
    public static final String REJECTED_ROBOTS_META_NOINDEX = 
            "REJECTED_ROBOTS_META_NOINDEX";
    public static final String REJECTED_TOO_DEEP = "REJECTED_TOO_DEEP";
    public static final String URLS_EXTRACTED = "URLS_EXTRACTED";

    public HttpDocCrawlEvent(String eventType, IDocCrawl docCrawl,
            Object subject) {
        super(eventType, docCrawl, subject);
    }

}
