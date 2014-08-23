/**
 * 
 */
package com.norconex.collector.http.crawler.pipe.doc;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.pipeline.IPipelineStage;
import com.norconex.collector.http.checksum.IHttpDocumentChecksummer;
import com.norconex.collector.http.crawler.HttpDocReference;
import com.norconex.collector.http.crawler.HttpDocReferenceState;

/**
 * @author Pascal Essiembre
 *
 */
/*default*/ class HttpDocumentChecksumStage 
        implements IPipelineStage<DocumentPipelineContext> {
    
    private static final Logger LOG = 
            LogManager.getLogger(ImportModuleStage.class);
    @Override
    public boolean process(DocumentPipelineContext ctx) {
        //TODO only if an INCREMENTAL run... else skip.
        IHttpDocumentChecksummer check = 
                ctx.getConfig().getHttpDocumentChecksummer();
        if (check == null) {
            return true;
        }
        String newDocChecksum = check.createChecksum(ctx.getDocument());
        ctx.getReference().setContentChecksum(newDocChecksum);
        String oldDocChecksum = null;
        HttpDocReference cachedURL = (HttpDocReference) 
                ctx.getReferenceStore().getCached(
                        ctx.getReference().getReference());
        if (cachedURL != null) {
            oldDocChecksum = cachedURL.getContentChecksum();
        } else {
            LOG.debug("ACCEPTED document checkum (new): URL=" 
                    + ctx.getReference().getReference());
            return true;
        }
        if (StringUtils.isNotBlank(newDocChecksum) 
                && Objects.equals(newDocChecksum, oldDocChecksum)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED document checkum (unmodified): URL=" 
                        + ctx.getReference().getReference());
            }
            ctx.getReference().setState(HttpDocReferenceState.UNMODIFIED);
            return false;
        }
        LOG.debug("ACCEPTED document checkum (modified): URL=" 
                + ctx.getReference().getReference());
        return true;
    }
}   