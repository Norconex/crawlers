/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.web.cmd.crawl.pipelines;

import static com.norconex.crawler.core.fetch.FetchDirective.DOCUMENT;
import static com.norconex.crawler.core.fetch.FetchDirective.METADATA;

import com.norconex.commons.lang.function.Predicates;
import com.norconex.crawler.core.cmd.crawl.pipelines.DocPipelines;
import com.norconex.crawler.core.cmd.crawl.pipelines.committer.CommitterPipeline;
import com.norconex.crawler.core.cmd.crawl.pipelines.committer.stages.CommitModuleStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.committer.stages.DocumentChecksumStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.committer.stages.DocumentDedupStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.committer.stages.DocumentPostProcessingStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.importer.ImporterPipeline;
import com.norconex.crawler.core.cmd.crawl.pipelines.importer.stages.DocumentFiltersStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.importer.stages.DocumentPreProcessingStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.importer.stages.ImportModuleStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.importer.stages.MetadataChecksumStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.importer.stages.MetadataDedupStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.importer.stages.MetadataFiltersStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.queue.QueuePipeline;
import com.norconex.crawler.core.cmd.crawl.pipelines.queue.stages.DepthValidationStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.queue.stages.QueueReferenceStage;
import com.norconex.crawler.core.cmd.crawl.pipelines.queue.stages.ReferenceFiltersStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.committer.stages.PostImportLinksStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.importer.WebImporterPipelineContext;
import com.norconex.crawler.web.cmd.crawl.pipelines.importer.stages.CanonicalStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.importer.stages.DelayResolverStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.importer.stages.HttpFetchStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.importer.stages.LinkExtractorStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.importer.stages.RecrawlableResolverStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.importer.stages.RobotsMetaCreateStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.importer.stages.RobotsMetaNoIndexStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.queue.stages.RobotsTxtFiltersStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.queue.stages.SitemapResolutionStage;
import com.norconex.crawler.web.cmd.crawl.pipelines.queue.stages.UrlNormalizerStage;

public final class WebDocPipelines {

    private WebDocPipelines() {
    }

    public static DocPipelines create() {
        return DocPipelines.builder()
                .queuePipeline(QueuePipeline.builder()
                        .stages(Predicates.allOf(
                                new DepthValidationStage(),
                                new ReferenceFiltersStage(),
                                new RobotsTxtFiltersStage(),
                                new UrlNormalizerStage(),
                                new SitemapResolutionStage(),
                                new QueueReferenceStage()))
                        .build())
                .importerPipeline(ImporterPipeline.builder().contextAdapter(
                        ctx -> ctx instanceof WebImporterPipelineContext wipc
                                ? wipc
                                : new WebImporterPipelineContext(ctx))
                        .stages(Predicates.allOf(
                                // if an orphan is reprocessed, it could be that
                                // it is no longer referenced because of
                                // deletion. Because of that, we need to process
                                // it again to find out so we ignore the
                                // "is re-crawlable" stage.

                                //TODO move to Core and add to FS as well?
                                new RecrawlableResolverStage(),

                                //TODO rename DelayResolver to HitInterval ??
                                //TODO move to Core and add to FS as well?
                                new DelayResolverStage(),

                                // When HTTP headers are fetched (HTTP "HEAD")
                                // before document:
                                new HttpFetchStage(METADATA),
                                new MetadataFiltersStage(METADATA),
                                new CanonicalStage(METADATA),
                                new MetadataChecksumStage(METADATA),
                                new MetadataDedupStage(METADATA),

                                // HTTP "GET" and onward:
                                new HttpFetchStage(DOCUMENT),
                                new CanonicalStage(DOCUMENT),
                                new RobotsMetaCreateStage(),
                                new LinkExtractorStage(),
                                new RobotsMetaNoIndexStage(),
                                new MetadataFiltersStage(DOCUMENT),
                                new MetadataChecksumStage(DOCUMENT),
                                new MetadataDedupStage(DOCUMENT),
                                new DocumentFiltersStage(),
                                new DocumentPreProcessingStage(),
                                new ImportModuleStage()))
                        .build())
                .committerPipeline(CommitterPipeline.builder()
                        .stages(Predicates.allOf(
                                new DocumentChecksumStage(),
                                new DocumentDedupStage(),
                                new DocumentPostProcessingStage(),
                                new PostImportLinksStage(),
                                new CommitModuleStage()))
                        .build())
                .build();
    }
}
