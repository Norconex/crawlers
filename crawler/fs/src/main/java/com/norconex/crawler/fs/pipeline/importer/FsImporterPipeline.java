/* Copyright 2013-2023 Norconex Inc.
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

public class FsImporterPipeline implements ImporterPipeline {

    private final Predicates<ImporterPipelineContext> stages =
        new Predicates<>(List.of(

            //--- METADATA ---
            // When the metadata fetch directive is enabled do the following
            // is executed
            new FileFetchStage(FetchDirective.METADATA),
            new MetadataFiltersStage(FetchDirective.METADATA),
            // Folder children done right after filter to give a chance
            // to reject the folder before getting its children.
            new FolderPathsExtractorStage(FetchDirective.METADATA),
            new MetadataChecksumStage(FetchDirective.METADATA),
            new MetadataDedupStage(FetchDirective.METADATA),

            //--- DOCUMENT ---
            new FileFetchStage(FetchDirective.DOCUMENT),
            new MetadataFiltersStage(FetchDirective.DOCUMENT),
            new FolderPathsExtractorStage(FetchDirective.DOCUMENT),
            new MetadataChecksumStage(FetchDirective.DOCUMENT),
            new MetadataDedupStage(FetchDirective.DOCUMENT),
            new DocumentFiltersStage(),
            new DocumentPreProcessingStage(),
            new ImportModuleStage()
        ));

    @Override
    public ImporterResponse apply(ImporterPipelineContext ctx) {
        var fsCtx = ctx instanceof FsImporterPipelineContext wipc
                ? wipc : new FsImporterPipelineContext(ctx);
        stages.test(fsCtx);
        return fsCtx.getImporterResponse();
    }
}

