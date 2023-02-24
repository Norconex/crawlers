/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.crawler.web.pipeline.importer;

import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.pipeline.ChecksumStageUtil;
import com.norconex.crawler.web.fetch.HttpMethod;

import lombok.NonNull;

class MetadataChecksumStage extends AbstractHttpImporterStage {

    public MetadataChecksumStage(@NonNull HttpMethod method) {
        super(method);
    }

    @Override
    boolean executeStage(HttpImporterPipelineContext ctx) {
        //TODO only if an INCREMENTAL run... else skip.
        if (ctx.wasHttpHeadPerformed(getHttpMethod())) {
            return true;
        }

        var check = ctx.getConfig().getMetadataChecksummer();
        if (check == null) {
            // NEW is default state (?)
            ctx.getDocRecord().setState(CrawlDocState.NEW);
            return true;
        }
        var headers = ctx.getDocument().getMetadata();
        var newHeadChecksum = check.createMetadataChecksum(headers);

        return ChecksumStageUtil.resolveMetaChecksum(
                newHeadChecksum, ctx, check);
    }
}