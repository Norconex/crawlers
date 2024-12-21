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
package com.norconex.crawler.core.stubs;

import static com.norconex.crawler.core.fetch.FetchDirective.DOCUMENT;
import static com.norconex.crawler.core.fetch.FetchDirective.METADATA;

import com.norconex.commons.lang.function.Predicates;
import com.norconex.crawler.core.mocks.fetch.MockFetchStage;
import com.norconex.crawler.core.pipelines.CrawlerPipelines;
import com.norconex.crawler.core.pipelines.committer.CommitterPipeline;
import com.norconex.crawler.core.pipelines.committer.stages.CommitModuleStage;
import com.norconex.crawler.core.pipelines.committer.stages.DocumentChecksumStage;
import com.norconex.crawler.core.pipelines.committer.stages.DocumentDedupStage;
import com.norconex.crawler.core.pipelines.committer.stages.DocumentPostProcessingStage;
import com.norconex.crawler.core.pipelines.importer.ImporterPipeline;
import com.norconex.crawler.core.pipelines.importer.stages.DocumentFiltersStage;
import com.norconex.crawler.core.pipelines.importer.stages.DocumentPreProcessingStage;
import com.norconex.crawler.core.pipelines.importer.stages.ImportModuleStage;
import com.norconex.crawler.core.pipelines.importer.stages.MetadataChecksumStage;
import com.norconex.crawler.core.pipelines.importer.stages.MetadataDedupStage;
import com.norconex.crawler.core.pipelines.importer.stages.MetadataFiltersStage;
import com.norconex.crawler.core.pipelines.queue.QueuePipeline;
import com.norconex.crawler.core.pipelines.queue.stages.DepthValidationStage;
import com.norconex.crawler.core.pipelines.queue.stages.QueueReferenceStage;
import com.norconex.crawler.core.pipelines.queue.stages.ReferenceFiltersStage;

public final class PipelineStubs {

    private PipelineStubs() {
    }

    public static CrawlerPipelines pipelines() {
        return CrawlerPipelines
                .builder()
                .queuePipeline(QueuePipeline
                        .builder()
                        .stages(Predicates.allOf(
                                new DepthValidationStage(),
                                new ReferenceFiltersStage(),
                                new QueueReferenceStage()))
                        .build())
                .importerPipeline(ImporterPipeline
                        .builder()
                        .stages(Predicates.allOf(
                                new MockFetchStage(METADATA),
                                new MetadataFiltersStage(METADATA),
                                new MetadataChecksumStage(METADATA),
                                new MetadataDedupStage(METADATA),
                                new MockFetchStage(DOCUMENT),
                                new MetadataFiltersStage(DOCUMENT),
                                new MetadataChecksumStage(DOCUMENT),
                                new MetadataDedupStage(DOCUMENT),
                                new DocumentFiltersStage(),
                                new DocumentPreProcessingStage(),
                                new ImportModuleStage()))
                        .build())
                .committerPipeline(CommitterPipeline
                        .builder()
                        .stages(Predicates.allOf(
                                new DocumentChecksumStage(),
                                new DocumentDedupStage(),
                                new DocumentPostProcessingStage(),
                                new CommitModuleStage()))
                        .build())
                .build();
    }
}
