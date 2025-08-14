/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.core2.mocks.fetch;

import com.norconex.crawler.core2.CrawlerException;
import com.norconex.crawler.core2.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core2.doc.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.core2.fetch.FetchDirective;
import com.norconex.crawler.core2.fetch.FetchException;

import lombok.NonNull;

/**
 * <p>Fetches (i.e. download/copy for processing) a document and/or its
 * metadata (e.g., file properties) depending on supplied
 * {@link FetchDirective}.</p>
 * @since 3.0.0 (Merge of former metadata and document fetcher stages).
 */
public class MockFetchStage extends AbstractImporterStage {

    public MockFetchStage(@NonNull FetchDirective directive) {
        super(directive);
    }

    /**
     * Only does something if appropriate. For instance,
     * if a separate HTTP HEAD request was NOT required to be performed,
     * this method will never get invoked for a HEAD method.
     * @param ctx pipeline context
     * @return <code>true</code> if we continue processing.
     */
    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {
        // If stage is for a directive that was disabled, skip
        if (!ctx.isFetchDirectiveEnabled(getFetchDirective())) {
            return true;
        }

        var docContext = ctx.getDocContext();
        var doc = docContext.getDoc();
        try {
            var response = ctx
                    .getCrawlSession()
                    .getCrawlContext()
                    .getFetcher()
                    .fetch(new MockFetchRequest(doc));
            var outcome = response.getProcessingOutcome();
            docContext.getCurrentCrawlEntry().setProcessingOutcome(outcome);
            doc.getMetadata().set(
                    "mock.alsoCached",
                    docContext.getPreviousCrawlEntry() != null);
        } catch (FetchException e) {
            throw new CrawlerException(
                    "Could not fetch document: " + doc.getReference(), e);
        }
        return true;
    }
}
