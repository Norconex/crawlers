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
package com.norconex.crawler.fs.doc.pipelines.importer.stages;

import java.util.Set;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.fs.doc.FsCrawlDocContext;
import com.norconex.crawler.fs.fetch.FolderPathsFetchRequest;
import com.norconex.crawler.fs.fetch.FolderPathsFetchResponse;
import com.norconex.crawler.fs.fetch.FsPath;

public class FolderPathsExtractorStage extends AbstractImporterStage {

    public FolderPathsExtractorStage(FetchDirective fetchDirective) {
        super(fetchDirective);
    }

    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {

        if (!ctx.isFetchDirectiveEnabled(getFetchDirective())
                || ctx.isMetadataDirectiveExecuted(getFetchDirective())) {
            return true;
        }

        var fetcher = ctx.getCrawlContext().getFetcher();

        var docContext = (FsCrawlDocContext) ctx.getDoc().getDocContext();
        if (docContext.isFolder()) {
            Set<FsPath> paths;
            try {

                //***************************************************************************************

                //TODO in driver, make the aggregator return something
                // that support/return the different type of responses;

                //***************************************************************************************

                var resp = (FolderPathsFetchResponse) fetcher
                        .fetch(new FolderPathsFetchRequest(ctx.getDoc()));
                //TODO check for response exception, provided we are setting
                // any
                paths = resp.getChildPaths();
            } catch (FetchException e) {
                throw new CrawlerException("Could not fetch child paths of: "
                        + docContext.getReference(), e);
            }
            for (FsPath fsPath : paths) {
                var newPath = new FsCrawlDocContext(
                        fsPath.getUri(), docContext.getDepth() + 1);
                newPath.setFile(fsPath.isFile());
                newPath.setFolder(fsPath.isFolder());
                ctx.getCrawlContext()
                        .getDocPipelines()
                        .getQueuePipeline()
                        .accept(
                                new QueuePipelineContext(
                                        ctx.getCrawlContext(), newPath));
            }
        }

        // On some file system, a folder could also be a file, so we
        // continue if it is a file, regardless of folder logic above.
        return docContext.isFile();
    }
}
