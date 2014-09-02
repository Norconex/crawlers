/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.pipeline.IPipelineStage;
import com.norconex.collector.http.checksum.IHttpHeadersChecksummer;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.doccrawl.HttpDocCrawl;
import com.norconex.collector.http.doccrawl.HttpDocCrawlState;

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
    public boolean process(DocumentPipelineContext ctx) {
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
            ctx.getDocCrawl().setState(HttpDocCrawlState.NEW);
            return false;
        }
        HttpMetadata headers = ctx.getMetadata();
        String newHeadChecksum = check.createChecksum(headers);
        ctx.getDocCrawl().setMetaChecksum(newHeadChecksum);
        String oldHeadChecksum = null;
        HttpDocCrawl cachedURL = (HttpDocCrawl) 
                ctx.getReferenceStore().getCached(
                        ctx.getDocCrawl().getReference());
        if (cachedURL != null) {
            oldHeadChecksum = cachedURL.getMetaChecksum();
        } else {
            LOG.debug("ACCEPTED document headers checkum (new): URL="
                    + ctx.getDocCrawl().getReference());
            ctx.getDocCrawl().setState(HttpDocCrawlState.NEW);
            return false;
        }
        if (StringUtils.isNotBlank(newHeadChecksum) 
                && Objects.equals(newHeadChecksum, oldHeadChecksum)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED document headers checkum (unmodified): URL="
                        + ctx.getDocCrawl().getReference());
            }
            ctx.getDocCrawl().setState(HttpDocCrawlState.UNMODIFIED);
            return true;
        }
        ctx.getDocCrawl().setState(HttpDocCrawlState.MODIFIED);
        LOG.debug("ACCEPTED document headers checkum (modified): URL=" 
                + ctx.getDocCrawl().getReference());
        return false;
    }
}