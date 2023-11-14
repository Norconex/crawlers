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
package com.norconex.crawler.core.pipeline.importer;

import java.util.List;

import com.norconex.commons.lang.function.Predicates;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.importer.response.ImporterResponse;

public class MockImporterPipeline implements ImporterPipeline {

    private final Predicates<ImporterPipelineContext> stages =
        new Predicates<>(List.of(
            new MetadataFiltersStage(FetchDirective.METADATA),
            new MetadataChecksumStage(FetchDirective.METADATA),
            new MetadataDedupStage(FetchDirective.METADATA),
            new MetadataFiltersStage(FetchDirective.DOCUMENT),
            new MetadataChecksumStage(FetchDirective.DOCUMENT),
            new MetadataDedupStage(FetchDirective.DOCUMENT),
            new DocumentFiltersStage(),
            new DocumentPreProcessingStage(),
            new ImportModuleStage()
        ));

    @Override
    public ImporterResponse apply(ImporterPipelineContext ctx) {
        stages.test(ctx);
        return ctx.getImporterResponse();
    }

//    @Override
//    public ImporterResponse DELETE_apply(ImporterPipelineContext ctx) {
//
//        //TODO make configurable to test multiple scenarios
//
//        // Simulate fetch
//        //TODO invoke mock fetch to test fetching??
//
//        var doc = ctx.getDocument();
//        doc.setInputStream(
//                new ByteArrayInputStream("Mock content.".getBytes()));
//
//        var rec = ctx.getDocRecord();
//        rec.setState(CrawlDocState.MODIFIED);
//        rec.setCrawlDate(ZonedDateTime.now());
//        rec.setCharset(UTF_8);
//        rec.setContentType(ContentType.HTML);
//        rec.setContentChecksum("mock-content-checksum");
//        rec.setMetaChecksum("mock-meta-checksum");
//
//        //MAYBE Those should be set reflectively based on DocRecord properties?
//        var meta = doc.getMetadata();
//
//        meta.set("mock.alsoCached", ctx.getCachedDocRecord() != null);
//
//        meta.set(DocMetadata.CONTENT_TYPE, rec.getContentType());
//        meta.set(DocMetadata.CONTENT_ENCODING, rec.getCharset());
//
//        return ctx.getCrawler().getImporter().importDocument(doc);
//    }
}
