/* Copyright 2014-2022 Norconex Inc.
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
package com.norconex.crawler.core.pipeline.importer;

import com.norconex.commons.lang.pipeline.IPipelineStage;
import com.norconex.importer.Importer;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.response.ImporterResponse;

/**
 * Common pipeline stage for importing documents.
 */
public class ImportModuleStage
            implements IPipelineStage<ImporterPipelineContext> {

    @Override
    public boolean execute(ImporterPipelineContext ctx) {
        Importer importer = ctx.getCrawler().getImporter();

        Doc doc = ctx.getDocument();

        boolean isContentTypeSet = doc.getDocRecord().getContentType() != null;

        ImporterResponse response = importer.importDocument(doc);
        ctx.setImporterResponse(response);

        //TODO is it possible for content type not to be set here??
        // We make sure to set it to save it to store so IRecrawlableResolver
        // has one to deal with
        if (!isContentTypeSet && response.getDocument() != null) {
            ctx.getDocRecord().setContentType(
                    response.getDocument().getDocRecord().getContentType());
        }

        return true;
    }
}