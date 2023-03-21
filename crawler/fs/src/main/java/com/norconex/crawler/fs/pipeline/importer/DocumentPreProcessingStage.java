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
package com.norconex.crawler.fs.pipeline.importer;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.pipeline.importer.AbstractImporterStage;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.fs.doc.FsDocumentProcessor;
import com.norconex.crawler.fs.fetch.FileFetcher;
import com.norconex.crawler.fs.util.Fs;

class DocumentPreProcessingStage extends AbstractImporterStage {
    @Override
    protected boolean executeStage(ImporterPipelineContext context) {
        var ctx = Fs.context(context);
        for (FsDocumentProcessor preProc :
                ctx.getConfig().getPreImportProcessors()) {
            preProc.processDocument(
                    (FileFetcher) ctx.getCrawler().getFetcher(),
                    ctx.getDocument());
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.DOCUMENT_PREIMPORTED)
                    .source(ctx.getCrawler())
                    .subject(preProc)
                    .crawlDocRecord(ctx.getDocRecord())
                    .build());
        }
        return true;
    }
}