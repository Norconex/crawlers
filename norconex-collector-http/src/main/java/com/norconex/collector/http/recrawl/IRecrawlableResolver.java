package com.norconex.collector.http.recrawl;

/**
 * Indicates whether a document that was successfully crawled on a previous
 * crawling session should be recrawled or not.  Documents not ready 
 * to be recrawled are not downloaded again (no HTTP calls will be made)
 * and are not committed.
 * @author Pascal Essiembre
 * @since 1.5.0
 */
public interface IRecrawlableResolver {

    /**
     * Whether a document recrawlable or not.
     * @param prevCrawlData data about previously crawled document
     * @return <code>true</code> if recrawlable
     */
    boolean isRecrawlable(PreviousCrawlData prevCrawlData);
}
