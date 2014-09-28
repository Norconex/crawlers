/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.crawler.event.CrawlerEvent;
import com.norconex.collector.http.checksum.IHttpHeadersChecksummer;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.commons.lang.pipeline.IPipelineStage;

/**
 * @author Pascal Essiembre
 *
 */
/*default*/ class HttpHeadersChecksumStage 
        implements IPipelineStage<DocumentPipelineContext> {

    private static final Logger LOG = 
            LogManager.getLogger(DocumentPipelineUtil.class);

    private final boolean useHttpHEADFetchHeaders;
    
    public HttpHeadersChecksumStage(boolean useHttpHEADFetchHeaders) {
        super();
        this.useHttpHEADFetchHeaders = useHttpHEADFetchHeaders;
    }

    @Override
    public boolean execute(DocumentPipelineContext ctx) {
        //TODO only if an INCREMENTAL run... else skip.
        if (useHttpHEADFetchHeaders && !ctx.isHttpHeadFetchEnabled()) {
            return true;
        }

        return !isHeadersChecksumRejected(ctx);
    }
    
    private boolean isHeadersChecksumRejected(DocumentPipelineContext ctx) {
        IHttpHeadersChecksummer check = 
                ctx.getConfig().getHttpHeadersChecksummer();
        if (check == null) {
            // NEW is default state (?)
            ctx.getDocCrawl().setState(HttpCrawlState.NEW);
            return false;
        }
        HttpMetadata headers = ctx.getMetadata();
        String newHeadChecksum = check.createChecksum(headers);
        ctx.getDocCrawl().setMetaChecksum(newHeadChecksum);
        String oldHeadChecksum = null;
        HttpCrawlData cachedURL = (HttpCrawlData) 
                ctx.getReferenceStore().getCached(
                        ctx.getDocCrawl().getReference());
        if (cachedURL != null) {
            oldHeadChecksum = cachedURL.getMetaChecksum();
        } else {
            LOG.debug("ACCEPTED document headers checkum (new): URL="
                    + ctx.getDocCrawl().getReference());
            ctx.getDocCrawl().setState(HttpCrawlState.NEW);
            return false;
        }
        if (StringUtils.isNotBlank(newHeadChecksum) 
                && Objects.equals(newHeadChecksum, oldHeadChecksum)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED document headers checkum (unmodified): URL="
                        + ctx.getDocCrawl().getReference());
            }
            ctx.getCrawler().fireDocCrawlEvent(new CrawlerEvent(
                    CrawlerEvent.REJECTED_UNMODIFIED, 
                    ctx.getDocCrawl(), check));
            ctx.getDocCrawl().setState(HttpCrawlState.UNMODIFIED);
            return true;
        }
        ctx.getDocCrawl().setState(HttpCrawlState.MODIFIED);
        LOG.debug("ACCEPTED document headers checkum (modified): URL=" 
                + ctx.getDocCrawl().getReference());
        return false;
    }
}