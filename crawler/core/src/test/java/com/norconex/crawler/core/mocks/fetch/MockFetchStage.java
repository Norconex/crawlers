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
package com.norconex.crawler.core.mocks.fetch;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;

import lombok.NonNull;

public class MockFetchStage extends AbstractImporterStage {

    public MockFetchStage(@NonNull FetchDirective directive) {
        super(directive);
    }

    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {
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
