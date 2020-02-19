/* Copyright 2010-2020 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.http.pipeline.importer;

import com.norconex.collector.core.checksum.IMetadataChecksummer;
import com.norconex.collector.core.pipeline.ChecksumStageUtil;
import com.norconex.collector.http.doc.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocMetadata;

/**
 * @author Pascal Essiembre
 *
 */
/*default*/ class MetadataChecksumStage extends AbstractImporterStage {

    private final boolean useHttpHEADFetchHeaders;

    public MetadataChecksumStage(boolean useHttpHEADFetchHeaders) {
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
            ctx.getCrawlReference().setState(HttpCrawlState.NEW);
            return true;
        }
        HttpDocMetadata headers = ctx.getMetadata();
        String newHeadChecksum = check.createMetadataChecksum(headers);

        return ChecksumStageUtil.resolveMetaChecksum(
                newHeadChecksum, ctx, check);
    }
}