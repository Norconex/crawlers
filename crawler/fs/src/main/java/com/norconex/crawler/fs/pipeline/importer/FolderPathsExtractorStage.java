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

import java.util.Set;

import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.pipeline.importer.AbstractImporterStage;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.fs.doc.FsDocRecord;
import com.norconex.crawler.fs.fetch.FileFetcher;
import com.norconex.crawler.fs.path.FsPath;

class FolderPathsExtractorStage extends AbstractImporterStage {

    public FolderPathsExtractorStage(FetchDirective fetchDirective) {
        super(fetchDirective);
    }

    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {

        if (ctx.wasMetadataDirectiveExecuted(getFetchDirective())) {
            return true;
        }

        var rec = (FsDocRecord) ctx.getDocRecord();
        if (rec.isFolder()) {
            Set<FsPath> paths;
            try {
                paths = ((FileFetcher) ctx.getCrawler().getFetcher())
                        .fetchChildPaths(ctx.getDocRecord().getReference());
            } catch (FetchException e) {
                throw new CrawlerException("Could not fetch child paths of: "
                        + ctx.getDocRecord().getReference(), e);
            }
            for (FsPath fsPath : paths) {
                var newPath = new FsDocRecord(
                        fsPath.getUri(), ctx.getDocRecord().getDepth() +1 );
                newPath.setFile(fsPath.isFile());
                newPath.setFolder(fsPath.isFolder());
                ctx.getCrawler().queueDocRecord(newPath);
            }
        }

        // On some file system, a folder could also be a file, so we
        // continue if it is a file, regardless of folder logic above.
        return rec.isFile();
    }
}
