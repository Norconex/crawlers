/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core.tasks.crawl.pipelines.importer.stages;

import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.tasks.crawl.operations.DocumentConsumer;
import com.norconex.crawler.core.tasks.crawl.pipelines.importer.ImporterPipelineContext;

public class DocumentPreProcessingStage extends AbstractImporterStage {
    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {
        for (DocumentConsumer preProc : ctx.getCrawler().getConfiguration()
                .getPreImportConsumers()) {
            preProc.accept(ctx.getCrawler().getFetcher(), ctx.getDoc());
            ctx.getCrawler().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.DOCUMENT_PREIMPORTED)
                            .source(ctx.getCrawler())
                            .subject(preProc)
                            .docContext(ctx.getDoc().getDocContext())
                            .build());
        }
        return true;
    }
}