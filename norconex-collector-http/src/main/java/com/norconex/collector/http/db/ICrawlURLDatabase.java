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
 *   <li><b>Deleted:</b> A URL was previously good in the cache but is no 
 *          longer.  XXX: we want this in DB???</li>
 * </ul>
 *    
 * @author Pascal Essiembre
 *
 */
interface ICrawlURLDatabase {

    
    /**
     * Queues a URL for future processing.
     * @param url the URL to eventually be processed
     * @param depth how many clicks away from starting URL(s)
     */
    
    //TODO should the method check if already processed first or crawler will do?

    void queue(CrawlURL crawlURL);
    //was: void addQueuedURL(String url, int depth);

    /**
     * Whether there are any URLs to process in the queue.
     * @return <code>true</code> if the queue is empty
     */
    boolean isQueueEmpty();
    //was: boolean hasQueuedURLs();
    
    /**
     * Gets the size of the URL queue (number of URLs left to process).
     * @return queue size
     */
    long getQueueSize();
    //was: int getQueuedURLCount();

    /**
     * Whether the given URL is in the queue or not (waiting to be processed).
     * @return <code>true</code> if the URL is in the queue
     */
    boolean isQueued(CrawlURL crawlURL);
    //was: boolean isQueuedURL(String url);

    /**
     * Returns the next URL to be processed and marks it as being "active"
     * (i.e. currently being processed).
     * @return next URL
     */
    CrawlURL next();
    //was: getNextQueuedURL();
    
    /**
     * Whether the given URL is currently being processed (i.e. active).
     * @param crawlURL the url
     * @return <code>true</code> if active
     */
    boolean isActive(CrawlURL crawlURL);
    //was: boolean isActiveURL(String url);
    
    
    /**
     * Gets the cached URL from previous time crawler was run
     * (e.g. for comparison purposes).
     * @param savedURL URL saved from previous run
     * @return url
     */
    CrawlURL getCached(String processedURL);
    //was: URLMemento getURLMemento(String processedURL);
    
    /**
     * Whether there are any URLs the the cache from a previous crawler 
     * run.
     * @return <code>true</code> if the cache is empty
     */
    boolean isCacheEmpty();
    //was: boolean isLastProcessedURLsEmpty();

    /**
     * Marks this URL as processed.  Processed URLs will not be processed again
     * in the same crawl run.
     * @param crawlURL
     */
    void processed(CrawlURL crawlURL);
    //was: void addProcessedURL(String url, CrawlURL memento);

    /**
     * Whether the given URL has been processed.
     * @param crawlURL url
     * @return <code>true</code> if processed
     */
    boolean isProcessed(CrawlURL crawlURL);
    //was: boolean isProcessedURL(String url);

    /**
     * Gets the number of URLs processed.
     * @return number of URLs processed.
     */
    long getProcessedCount();
    //was: int getProcessedURLCount();

    /**
     * Queues a segment of all URLs cached from a previous run. 
     * This method will be called several time, until the cache becomes empty.
     * How many URLs gets queued from the cache when this method is called
     * is left to implementors, based on performance in moving from cache to
     * queue.
     */
    void queueCacheSegment();
    //was: void copyLastProcessedURLBatchToQueue(int batchSize);
    
    
    
    //TODO: Should we have deleted here??? Or a deleted document is simply a
    // processed one from a database standpoint???
    /**
     * Marks this URL as deleted. A URL should only be deleted when it was
     * successfully crawled on a previous run, but is not rejected for some
     * reason.
     * @param crawlURL the URL
     */
    void delete(CrawlURL crawlURL);
    //was: boolean shouldDeleteURL(String url, CrawlURL memento);
    
    
    
    
    //TODO have a isDeleted() ???
    
    
    //NO EQUIVALENT??  Try to do within Store
    //boolean hasActiveURLs();

    //RENAMED:
    
    
    
    

    
//    class QueuedURL {
//        private int depth;
//        private String url;
//        int getDepth() {
//            return depth;
//        }
//        String getUrl() {
//            return url;
//        }
//    }
    
    
//    class URLMemento{
//    	private CrawlStatus status;
//    	final private int depth;
//    	private String headChecksum;
//    	private String docChecksum;
//		URLMemento(CrawlStatus status, int depth) {
//			super();
//			this.status = status;
//			this.depth = depth;
//		}
//		int getDepth() {
//			return depth;
//		}
//		String getHeadChecksum() {
//			return headChecksum;
//		}
//		void setHeadChecksum(String headChecksum) {
//			this.headChecksum = headChecksum;
//		}
//		String getDocChecksum() {
//			return docChecksum;
//		}
//		void setDocChecksum(String docChecksum) {
//			this.docChecksum = docChecksum;
//		}
//		CrawlStatus getStatus() {
//			return status;
//		}
//		void setStatus(CrawlStatus status) {
//			this.status = status;
//		}
//		@Override
//		String toString() {
//			Properties props = new Properties();
//			props.setProperty("s", status.toString());
//			if (headChecksum != null) {
//				props.setProperty("hc", headChecksum);
//			}
//			if (docChecksum != null) {
//                props.setProperty("dc", docChecksum);
//			}
//			props.setProperty("d", Integer.toString(depth));
//			StringWriter w = new StringWriter();
//			try {
//				props.store(w, null);
//			} catch (IOException e) {
//				throw new HttpCollectorException(
//						"Could not persist ProcessedURL: " 
//								+ props.toString(), e);
//			}
//			return w.toString();
//		}
//		static URLMemento fromString(String str) {
//			Properties props = new Properties();
//			StringReader r = new StringReader(str);
//			try {
//				props.load(r);
//				r.close();
//			} catch (IOException e) {
//				throw new HttpCollectorException(
//						"Could not parse processed URL from String: " + str, e);
//			}
//			URLMemento url = new URLMemento(
//					URLStatus.valueOf(props.getProperty("s")),
//					Integer.parseInt(props.getProperty("d")));
//			url.setDocChecksum(props.getProperty("dc"));
//			url.setHeadChecksum(props.getProperty("hc"));
//			return url;
//		}
//    }
}
