/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.crawler.event.CrawlerEvent;
import com.norconex.collector.http.checksum.IHttpDocumentChecksummer;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.commons.lang.pipeline.IPipelineStage;

/**
 * @author Pascal Essiembre
 *
 */
/*default*/ class DocumentChecksumStage 
        implements IPipelineStage<PostImportPipelineContext> {
    
    private static final Logger LOG = 
            LogManager.getLogger(ImportModuleStage.class);
    @Override
    public boolean execute(PostImportPipelineContext ctx) {
        //TODO only if an INCREMENTAL run... else skip.
        IHttpDocumentChecksummer check = 
                ctx.getConfig().getHttpDocumentChecksummer();
        if (check == null) {
            // NEW is default state (?)
            ctx.getDocCrawl().setState(HttpCrawlState.NEW);
            return true;
        }
        String newDocChecksum = check.createChecksum(ctx.getDocument());
        ctx.getDocCrawl().setContentChecksum(newDocChecksum);
        String oldDocChecksum = null;
        HttpCrawlData cachedURL = (HttpCrawlData) 
                ctx.getDocCrawlStore().getCached(
                        ctx.getDocCrawl().getReference());
        if (cachedURL != null) {
            oldDocChecksum = cachedURL.getContentChecksum();
        } else {
            LOG.debug("ACCEPTED document checkum (new): URL=" 
                    + ctx.getDocCrawl().getReference());
            ctx.getDocCrawl().setState(HttpCrawlState.NEW);
            return true;
        }
        if (StringUtils.isNotBlank(newDocChecksum) 
                && Objects.equals(newDocChecksum, oldDocChecksum)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED document checkum (unmodified): URL=" 
                        + ctx.getDocCrawl().getReference());
            }
            ctx.getCrawler().fireDocCrawlEvent(new CrawlerEvent(
                    CrawlerEvent.REJECTED_UNMODIFIED, 
                    ctx.getDocCrawl(), check));
            ctx.getDocCrawl().setState(HttpCrawlState.UNMODIFIED);
            return false;
        }
        LOG.debug("ACCEPTED document checkum (modified): URL=" 
                + ctx.getDocCrawl().getReference());
        ctx.getDocCrawl().setState(HttpCrawlState.MODIFIED);
        return true;
    }
}   