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
package com.norconex.crawler.fs.doc.pipelines;

import static com.norconex.crawler.core.fetch.FetchDirective.DOCUMENT;
import static com.norconex.crawler.core.fetch.FetchDirective.METADATA;

import com.norconex.commons.lang.function.Predicates;
import com.norconex.crawler.core.commands.crawl.task.pipelines.DocPipelines;
import com.norconex.crawler.core.commands.crawl.task.pipelines.committer.CommitterPipeline;
import com.norconex.crawler.core.commands.crawl.task.pipelines.committer.stages.CommitModuleStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.committer.stages.DocumentChecksumStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.committer.stages.DocumentDedupStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.committer.stages.DocumentPostProcessingStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.importer.ImporterPipeline;
import com.norconex.crawler.core.commands.crawl.task.pipelines.importer.stages.DocumentFiltersStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.importer.stages.DocumentPreProcessingStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.importer.stages.ImportModuleStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.importer.stages.MetadataChecksumStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.importer.stages.MetadataDedupStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.importer.stages.MetadataFiltersStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.queue.QueuePipeline;
import com.norconex.crawler.core.commands.crawl.task.pipelines.queue.stages.DepthValidationStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.queue.stages.QueueReferenceStage;
import com.norconex.crawler.core.commands.crawl.task.pipelines.queue.stages.ReferenceFiltersStage;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.fs.doc.pipelines.importer.stages.FileFetchStage;
import com.norconex.crawler.fs.doc.pipelines.importer.stages.FolderPathsExtractorStage;

public final class FsDocPipelines {

    private static final DocPipelines PIPELINE = DocPipelines
            .builder()
            .queuePipeline(QueuePipeline
                    .builder()
                    //                    .initializer(new CoreQueueInitializer())
                    .stages(Predicates.allOf(
                            new DepthValidationStage(),
                            new ReferenceFiltersStage(),
                            new QueueReferenceStage()))
                    .build())
            .importerPipeline(ImporterPipeline
                    .builder()
                    .stages(Predicates.allOf(
                            //--- METADATA ---
                            // When the metadata fetch directive is enabled,
                            // the following is executed
                            new FileFetchStage(
                                    METADATA),
                            new MetadataFiltersStage(
                                    METADATA),
                            // Child folders done right after filter to give a
                            // chance to reject the folder before getting its
                            // children.
                            new FolderPathsExtractorStage(
                                    METADATA),
                            new MetadataChecksumStage(
                                    METADATA),
                            new MetadataDedupStage(
                                    FetchDirective.METADATA),

                            //--- DOCUMENT ---
                            new FileFetchStage(
                                    DOCUMENT),
                            new MetadataFiltersStage(
                                    DOCUMENT),
                            new FolderPathsExtractorStage(
                                    DOCUMENT),
                            new MetadataChecksumStage(
                                    DOCUMENT),
                            new MetadataDedupStage(
                                    DOCUMENT),
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

    private FsDocPipelines() {
    }

    public static DocPipelines get() {
        return PIPELINE;
    }
}
