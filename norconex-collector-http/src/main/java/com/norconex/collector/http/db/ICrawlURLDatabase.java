package com.norconex.collector.http.db;


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
     * Queues a URL for future processing.
     * @param url the URL to eventually be processed
     * @param depth how many clicks away from starting URL(s)
     * @return a crawl url instance
     */
    void queue(String url, int depth);

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
     * @param crawlURL the url
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
}
