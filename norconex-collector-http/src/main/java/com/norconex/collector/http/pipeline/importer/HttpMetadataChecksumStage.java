/**
 * 
 */
package com.norconex.collector.http.pipeline.importer;

import com.norconex.collector.core.checksum.IMetadataChecksummer;
import com.norconex.collector.core.pipeline.ChecksumStageUtil;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpMetadata;

/**
 * @author Pascal Essiembre
 *
 */
/*default*/ class HttpMetadataChecksumStage extends AbstractImporterStage {

    private final boolean useHttpHEADFetchHeaders;
    
    public HttpMetadataChecksumStage(boolean useHttpHEADFetchHeaders) {
        super();
        this.useHttpHEADFetchHeaders = useHttpHEADFetchHeaders;
    }

    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        //TODO only if an INCREMENTAL run... else skip.
        if (useHttpHEADFetchHeaders && !ctx.isHttpHeadFetchEnabled()) {
            return true;
        }
        return isHeadersChecksumAccepted(ctx);
    }
    
    private boolean isHeadersChecksumAccepted(HttpImporterPipelineContext ctx) {
        IMetadataChecksummer check = 
                ctx.getConfig().getMetadataChecksummer();
        if (check == null) {
            // NEW is default state (?)
            ctx.getCrawlData().setState(HttpCrawlState.NEW);
            return true;
        }
        HttpMetadata headers = ctx.getMetadata();
        String newHeadChecksum = check.createMetadataChecksum(headers);

        return ChecksumStageUtil.resolveMetaChecksum(
                newHeadChecksum, ctx, check);
    }
}