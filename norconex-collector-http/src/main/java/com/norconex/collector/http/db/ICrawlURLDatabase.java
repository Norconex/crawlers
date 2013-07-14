/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.db;

import com.norconex.collector.http.crawler.CrawlURL;


/**
 * <p>Database implementation holding necessary information about all URL 
 * crawling activities, what crawling stages URLs are in.  
 * The few stages a URL can have are:</p>
 * <ul>
 *   <li><b>Queued:</b> URLs extracted from documents are first queued for 
 *       future processing.</li>
 *   <li><b>Active:</b> A URL is being processed.</li>
 *   <li><b>Processed:</b> A URL has been processed.  If the same URL is 
 *       encountered again during the same run, it will be ignored.</li>
 *   <li><b>Cached:</b> When crawling is over, processed URLs will be cached on 
 *       the next run.</li>
 * </ul>
 *    
 * @author Pascal Essiembre
 */
public interface ICrawlURLDatabase {

    /**
     * <p>
     * Queues a URL for future processing. 
     * @param url the URL to eventually be processed
     */
    void queue(CrawlURL url);

    /**
     * Whether there are any URLs to process in the queue.
     * @return <code>true</code> if the queue is empty
     */
    boolean isQueueEmpty();
    
    /**
     * Gets the size of the URL queue (number of URLs left to process).
     * @return queue size
     */
    int getQueueSize();

    /**
     * Whether the given URL is in the queue or not (waiting to be processed).
     * @param url url
     * @return <code>true</code> if the URL is in the queue
     */
    boolean isQueued(String url);
    
    /**
     * Returns the next URL to be processed and marks it as being "active"
     * (i.e. currently being processed).
     * @return next URL
     */
    CrawlURL next();
    
    /**
     * Whether the given URL is currently being processed (i.e. active).
     * @param url the url
     * @return <code>true</code> if active
     */
    boolean isActive(String url);

    /**
     * Gets the number of active URLs (currently being processed).
     * @return number of active URLs.
     */
    int getActiveCount();
    
    /**
     * Gets the cached URL from previous time crawler was run
     * (e.g. for comparison purposes).
     * @param cacheURL URL cached from previous run
     * @return url
     */
    CrawlURL getCached(String cacheURL);
    
    /**
     * Whether there are any URLs the the cache from a previous crawler 
     * run.
     * @return <code>true</code> if the cache is empty
     */
    boolean isCacheEmpty();

    /**
     * Marks this URL as processed.  Processed URLs will not be processed again
     * in the same crawl run.
     * @param crawlURL
     */
    void processed(CrawlURL crawlURL);

    /**
     * Whether the given URL has been processed.
     * @param url url
     * @return <code>true</code> if processed
     */
    boolean isProcessed(String url);

    /**
     * Gets the number of URLs processed.
     * @return number of URLs processed.
     */
    int getProcessedCount();

    /**
     * Queues URLs cached from a previous run so they can be processed again.
     * This method is normally called when a job is done crawling,
     * and entries remain in the cache.  Those are re-processed in case
     * they changed or are no longer valid. 
     */
    void queueCache();
    
    /**
     * Whether a url has been deleted.  To find this out, the URL has to be 
     * of an invalid state (e.g. NOT_FOUND) and must exists in the URL cache
     * in a valid state.
     * @param crawlURL the URL
     */
    boolean isVanished(CrawlURL crawlURL);
    
    /**
     * Mark a URL "root" (e.g. http://www.example.com) as having its sitemap
     * resolved.
     * @param urlRoot url root
     */
    void sitemapResolved(String urlRoot);
    
    /**
     * Whether the given URL "root" (e.g. http://www.example.com) has its
     * sitemap resolved.
     * @param urlRoot
     * @return <code>true</code> if already resolved
     */
    boolean isSitemapResolved(String urlRoot);
}
