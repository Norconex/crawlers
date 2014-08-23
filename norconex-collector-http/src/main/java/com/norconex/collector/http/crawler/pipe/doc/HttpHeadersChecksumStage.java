/**
 * 
 */
package com.norconex.collector.http.crawler.pipe.doc;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.pipeline.IPipelineStage;
import com.norconex.collector.http.checksum.IHttpHeadersChecksummer;
import com.norconex.collector.http.crawler.HttpDocReference;
import com.norconex.collector.http.crawler.HttpDocReferenceState;
import com.norconex.collector.http.doc.HttpMetadata;

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

        if (isHeadersChecksumRejected(ctx)) {
            ctx.getReference().setState(HttpDocReferenceState.UNMODIFIED);
            return false;
        }
        return true;
    }
    
    private boolean isHeadersChecksumRejected(DocumentPipelineContext ctx) {
        IHttpHeadersChecksummer check = 
                ctx.getConfig().getHttpHeadersChecksummer();
        if (check == null) {
            return false;
        }
        HttpMetadata headers = ctx.getMetadata();
        String newHeadChecksum = check.createChecksum(headers);
        ctx.getReference().setMetaChecksum(newHeadChecksum);
        String oldHeadChecksum = null;
        HttpDocReference cachedURL = (HttpDocReference) 
                ctx.getReferenceStore().getCached(
                        ctx.getReference().getReference());
        if (cachedURL != null) {
            oldHeadChecksum = cachedURL.getMetaChecksum();
        } else {
            LOG.debug("ACCEPTED document headers checkum (new): URL="
                    + ctx.getReference().getReference());
            return false;
        }
        if (StringUtils.isNotBlank(newHeadChecksum) 
                && Objects.equals(newHeadChecksum, oldHeadChecksum)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED document headers checkum (unmodified): URL="
                        + ctx.getReference().getReference());
            }
            return true;
        }
        LOG.debug("ACCEPTED document headers checkum (modified): URL=" 
                + ctx.getReference().getReference());
        return false;
    }
}