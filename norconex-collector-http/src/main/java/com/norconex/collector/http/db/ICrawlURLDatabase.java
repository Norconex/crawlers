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
public interface ICrawlURLDatabase {

    
    /**
     * Queues a URL for future processing.
     * @param url the URL to eventually be processed
     * @param depth how many clicks away from starting URL(s)
     * @return a crawl url instance
     */
    
    //TODO should the method check if already processed first or crawler will do?

    CrawlURL queue(String url, int depth);
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
    int getQueueSize();
    //was: int getQueuedURLCount();

    /**
     * Whether the given URL is in the queue or not (waiting to be processed).
     * @param url url
     * @return <code>true</code> if the URL is in the queue
     */
    boolean isQueued(String url);
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
    boolean isActive(String url);
    //was: boolean isActiveURL(String url);
    

    /**
     * Gets the number of active URLs (currently being processed).
     * @return number of active URLs.
     */
    int getActiveCount();
    //was: boolean hasActiveURLs();

    
    /**
     * Gets the cached URL from previous time crawler was run
     * (e.g. for comparison purposes).
     * @param cacheURL URL cached from previous run
     * @return url
     */
    CrawlURL getCached(String cacheURL);
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
     * @param url url
     * @return <code>true</code> if processed
     */
    boolean isProcessed(String url);
    //was: boolean isProcessedURL(String url);

    /**
     * Gets the number of URLs processed.
     * @return number of URLs processed.
     */
    int getProcessedCount();
    //was: int getProcessedURLCount();

    /**
     * Queues URLs cached from a previous run so they can be processed again.
     * This method is normally called when a job is done crawling,
     * and entries remain in the cache.  Those are re-processed in case
     * they changed or are no longer valid. 
     */
    void queueCache();
    //was: void copyLastProcessedURLBatchToQueue(int batchSize);
    
    
    
    //TODO: Should we have deleted here??? Or a deleted document is simply a
    // processed one from a database standpoint???
    /**
     * Whether a url has been deleted.  To find this out, the URL has to be 
     * of an invalid state (e.g. NOT_FOUND) and must exists in the URL cache
     * in a valid state.
     * @param crawlURL the URL
     */
    boolean isVanished(CrawlURL crawlURL);
    //was: boolean shouldDeleteURL(String url, CrawlURL memento);
    
    
    
    
    //TODO have a isDeleted() ???
    
    

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
