/* Copyright 2023 Norconex Inc.
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

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.processor.HttpDocumentProcessor;

/**
 * Document pre-processing.
 */
class DocumentPreProcessingStage extends AbstractHttpImporterStage {
    @Override
    boolean executeStage(HttpImporterPipelineContext ctx) {
        if (ctx.getConfig().getPreImportProcessors() != null) {
            for (HttpDocumentProcessor preProc :
                    ctx.getConfig().getPreImportProcessors()) {
                preProc.processDocument(
                        (HttpFetcher) ctx.getCrawler().getFetcher(),
                        ctx.getDocument());
                ctx.fire(CrawlerEvent.builder()
                        .name(CrawlerEvent.DOCUMENT_PREIMPORTED)
                        .source(ctx.getCrawler())
                        .subject(preProc)
                        .crawlDocRecord(ctx.getDocRecord())
                        .build());
            }
        }
        return true;
    }
}