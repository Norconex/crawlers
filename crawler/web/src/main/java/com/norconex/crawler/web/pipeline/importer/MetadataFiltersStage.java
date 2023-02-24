/* Copyright 2020-2023 Norconex Inc.
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
import com.norconex.crawler.core.pipeline.DocumentPipelineUtil;
import com.norconex.crawler.web.fetch.HttpMethod;

import lombok.NonNull;

/**
 * Perform filtering of documents based on document metadata.
 * The metadata typically consists of HTTP response headers and
 * collector-generated metadata.
 *
 * This stage is only executing filters once, from a HEAD request if
 * configured to perform a separate HEAD request, or otherwise from a GET
 * request.
 *
 * @since 3.0.0 (merge of former metadata HEAD and GET filter stages)
 */
class MetadataFiltersStage extends AbstractHttpImporterStage {

    public MetadataFiltersStage(@NonNull HttpMethod method) {
        super(method);
    }

    @Override
    boolean executeStage(HttpImporterPipelineContext ctx) {
        if (ctx.wasHttpHeadPerformed(getHttpMethod())) {
            return true;
        }

        if (DocumentPipelineUtil.isRejectedByMetadataFilters(ctx)) {
            ctx.getDocRecord().setState(CrawlDocState.REJECTED);
            return false;
        }
        return true;
    }
}