/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.pipelines.importer.stages;

import java.util.function.Predicate;

import com.norconex.crawler.core.cmd.crawl.pipelines.importer.ImporterPipelineContext;
import com.norconex.importer.doc.Doc;

/**
 * Common pipeline stage for importing documents.
 */
public class ImportModuleStage implements Predicate<ImporterPipelineContext> {

    @Override
    public boolean test(ImporterPipelineContext ctx) {
        var importer = ctx.getCrawlerContext().getImporter();

        Doc doc = ctx.getDoc();

        var isContentTypeSet = doc.getDocContext().getContentType() != null;
        var response = importer.importDocument(doc);
        ctx.setImporterResponse(response);

        //TODO is it possible for content type not to be set here??
        // We make sure to set it to save it to store so IRecrawlableResolver
        // has one to deal with
        if (!isContentTypeSet && response.getDoc() != null) {
            doc.getDocContext().setContentType(
                    response.getDoc().getDocContext().getContentType());
        }

        return true;
    }
}
