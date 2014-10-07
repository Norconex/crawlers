/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
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