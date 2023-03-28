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

import java.util.List;

import com.norconex.commons.lang.function.Predicates;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.pipeline.importer.DocumentFiltersStage;
import com.norconex.crawler.core.pipeline.importer.DocumentPreProcessingStage;
import com.norconex.crawler.core.pipeline.importer.ImportModuleStage;
import com.norconex.crawler.core.pipeline.importer.ImporterPipeline;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.core.pipeline.importer.MetadataChecksumStage;
import com.norconex.crawler.core.pipeline.importer.MetadataDedupStage;
import com.norconex.crawler.core.pipeline.importer.MetadataFiltersStage;
import com.norconex.importer.response.ImporterResponse;

/**
 * All execution steps of a document processing from the moment it is
 * obtained from queue up to importing it.
 */
public class WebImporterPipeline implements ImporterPipeline {


    private final Predicates<ImporterPipelineContext> stages =
        new Predicates<>(List.of(
            // if an orphan is reprocessed, it could be that it is no longer
            // referenced because of deletion.  Because of that, we need
            // to process it again to find out so we ignore the
            // "is re-crawlable" stage.

            // TODO have this flag part of context and make this pipeline
            // defined statically
            // TODO move to Core and add to FS as well?
            new RecrawlableResolverStage(),

            //TODO rename DelayResolver to HitInterval ??
            // TODO move to Core and add to FS as well?
            new DelayResolverStage(),

            // When HTTP headers are fetched (HTTP "HEAD") before document:
            new HttpFetchStage(FetchDirective.METADATA),
            new MetadataFiltersStage(FetchDirective.METADATA),
            new CanonicalStage(FetchDirective.METADATA),
            new MetadataChecksumStage(FetchDirective.METADATA),
            new MetadataDedupStage(FetchDirective.METADATA),

            // HTTP "GET" and onward:
            new HttpFetchStage(FetchDirective.DOCUMENT),
            new CanonicalStage(FetchDirective.DOCUMENT),
            new RobotsMetaCreateStage(),
            new LinkExtractorStage(),
            new RobotsMetaNoIndexStage(),
            new MetadataFiltersStage(FetchDirective.DOCUMENT),
            new MetadataChecksumStage(FetchDirective.DOCUMENT),
            new MetadataDedupStage(FetchDirective.DOCUMENT),
            new DocumentFiltersStage(),
            new DocumentPreProcessingStage(),
            new ImportModuleStage()
        ));

    @Override
    public ImporterResponse apply(ImporterPipelineContext ctx) {
        var webCtx = ctx instanceof WebImporterPipelineContext wipc
                ? wipc : new WebImporterPipelineContext(ctx);
        stages.test(webCtx);
        return webCtx.getImporterResponse();
    }
}
