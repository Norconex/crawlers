/**
 * 
 */
package com.norconex.collector.http.crawler;

import com.norconex.collector.core.crawler.event.CrawlerEvent;
import com.norconex.collector.core.data.ICrawlData;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpCrawlerEvent extends CrawlerEvent {

    public static final String REJECTED_ROBOTS_TXT = 
            "REJECTED_ROBOTS_TXT";
    public static final String CREATED_ROBOTS_META = "CREATED_ROBOTS_META";
    public static final String REJECTED_ROBOTS_META_NOINDEX = 
            "REJECTED_ROBOTS_META_NOINDEX";
    public static final String REJECTED_TOO_DEEP = "REJECTED_TOO_DEEP";
    public static final String URLS_EXTRACTED = "URLS_EXTRACTED";
    
    public HttpCrawlerEvent(String eventType, ICrawlData crawlData,
            Object subject) {
        super(eventType, crawlData, subject);
    }

}
