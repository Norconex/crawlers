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
package com.norconex.crawler.fs.pipeline.committer;

import java.util.function.Predicate;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.pipeline.DocumentPipelineContext;
import com.norconex.crawler.fs.crawler.FsCrawlerConfig;
import com.norconex.crawler.fs.fetch.FileFetcher;
import com.norconex.crawler.fs.processor.FsDocumentProcessor;

class DocumentPostProcessingStage
        implements Predicate<DocumentPipelineContext> {

    @Override
    public boolean test(DocumentPipelineContext ctx) {
        for (FsDocumentProcessor postProc :
                ((FsCrawlerConfig) ctx.getConfig()).getPostImportProcessors()) {
            postProc.processDocument(
                    (FileFetcher) ctx.getCrawler().getFetcher(),
                    ctx.getDocument());
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.DOCUMENT_POSTIMPORTED)
                    .source(ctx.getCrawler())
                    .subject(postProc)
                    .crawlDocRecord(ctx.getDocRecord())
                    .build());
        }
        return true;
    }
}