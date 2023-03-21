/* Copyright 2013-2018 Norconex Inc.
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
import com.norconex.crawler.core.pipeline.importer.ImportModuleStage;
import com.norconex.crawler.core.pipeline.importer.ImporterPipeline;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.core.pipeline.importer.MetadataChecksumStage;
import com.norconex.crawler.core.pipeline.importer.MetadataDedupStage;
import com.norconex.crawler.core.pipeline.importer.MetadataFiltersStage;
import com.norconex.importer.response.ImporterResponse;

/**
 * @author Pascal Essiembre
 *
 */
public class FsImporterPipeline implements ImporterPipeline {

    private final Predicates<ImporterPipelineContext> STAGES =
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
        STAGES.test(fsCtx);
        return fsCtx.getImporterResponse();
    }

//    public FsImporterPipeline(boolean isKeepDownloads) {
////        addStage(new FolderPathsExtractorStage());
////        addStage(new FileMetadataFetcherStage());
////        addStage(new FileMetadataFiltersStage());
////        addStage(new FileMetadataChecksumStage());
//        addStage(new DocumentFetchStage());
//        if (isKeepDownloads) {
//            addStage(new SaveDocumentStage());
//        }
//        addStage(new DocumentFiltersStage());
//        addStage(new DocumentPreProcessingStage());
//        addStage(new ImportModuleStage());
//    }
//
//    //--- Folder Path Extractor ------------------------------------------------
//    // Extract paths to queue them and stop processing this folder
//    private static class FolderPathsExtractorStage
//            extends DELETE_AbstractFsImporterStage {
//        @Override
//        public boolean executeStage(FsImporterPipelineContext ctx) {
//            try {
//                var file = ctx.getFileObject();
//                if (file.getType() == FileType.FOLDER) {
//                    var files = file.getChildren();
//                    for (FileObject childFile : files) {
//                        // Special chars such as # can be valid in local
//                        // file names, so get path from toString on local files,
//                        // which returns the unencoded path (github #47).
//                        var ref = childFile.getName().getURI();
//                        if (childFile instanceof LocalFile) {
//                            ref = childFile.getName().toString();
//                        }
//                        BaseCrawlData crawlData = new BaseCrawlData(ref);
//                        BasePipelineContext newContext =
//                                new BasePipelineContext(ctx.getCrawler(),
//                                        ctx.getCrawlDataStore(), crawlData);
//                        new FsQueuePipeline().execute(newContext);
//                    }
//                    return false;
//                }
//                return true;
//            } catch (Exception e) {
//                ctx.getCrawlData().setState(CrawlState.ERROR);
//                ctx.fireCrawlerEvent(CrawlerEvent.REJECTED_ERROR,
//                        ctx.getCrawlData(), this);
//                throw new CollectorException(
//                        "Cannot extract folder paths: "
//                                + ctx.getCrawlData().getReference(), e);
//            }
//        }
//    }

//
//    //--- Metadata filters -----------------------------------------------------
//    private static class FileMetadataFiltersStage
//            extends DELETE_AbstractFsImporterStage {
//        @Override
//        public boolean executeStage(FsImporterPipelineContext ctx) {
//            if (ctx.getConfig().getMetadataFilters() != null
//                    && ImporterPipelineUtil.isHeadersRejected(ctx)) {
//                ctx.getCrawlData().setState(CrawlState.REJECTED);
//                return false;
//            }
//            return true;
//        }
//    }


//    //--- File Metadata Fetcher ------------------------------------------------
//    private static class FileMetadataFetcherStage
//            extends DELETE_AbstractFsImporterStage {
//        @Override
//        public boolean executeStage(FsImporterPipelineContext ctx) {
//            BaseCrawlData crawlData = ctx.getCrawlData();
//            IFileMetadataFetcher metaFetcher =
//                    ctx.getConfig().getMetadataFetcher();
//            FileMetadata metadata = ctx.getMetadata();
//
//            //TODO consider passing original metadata instead?
//            var newMeta = new Properties(
//                    metadata.isCaseInsensitiveKeys());
//            var fileObject = ctx.getFileObject();
//
//            CrawlState state = metaFetcher.fetchMetadada(fileObject, newMeta);
//
//            metadata.putAll(newMeta);
//
//            //--- Apply Metadata to document ---
//            // TODO are there headers to enhance first based on attributes
//            // (like http collector)?
//            FileDocument doc = ctx.getDocument();
//            if (doc.getContentType() == null) {
//                doc.setContentType(ContentType.valueOf(metadata.getString(
//                        FileMetadata.COLLECTOR_CONTENT_TYPE)));
//                doc.setContentEncoding(metadata.getString(
//                        FileMetadata.COLLECTOR_CONTENT_ENCODING));
//            }
//
//            crawlData.setState(state);
//            if (!state.isGoodState()) {
//                String eventType;
//                if (state.isOneOf(CrawlState.NOT_FOUND)) {
//                    eventType = CrawlerEvent.REJECTED_NOTFOUND;
//                } else {
//                    eventType = CrawlerEvent.REJECTED_BAD_STATUS;
//                }
//                ctx.fireCrawlerEvent(eventType, crawlData, fileObject);
//                return false;
//            }
//            ctx.fireCrawlerEvent(CrawlerEvent.DOCUMENT_METADATA_FETCHED,
//                    crawlData, fileObject);
//            return true;
//        }
//    }

//    //--- HTTP Document Checksum -----------------------------------------------
//    private static class FileMetadataChecksumStage
//            extends DELETE_AbstractFsImporterStage {
//        @Override
//        public boolean executeStage(FsImporterPipelineContext ctx) {
//            //TODO only if an INCREMENTAL run... else skip.
//
//            IMetadataChecksummer check =
//                    ctx.getConfig().getMetadataChecksummer();
//            if (check != null) {
//                String newChecksum =
//                        check.createMetadataChecksum(ctx.getMetadata());
//                return ChecksumStageUtil.resolveMetaChecksum(
//                        newChecksum, ctx, this);
//            }
//            return true;
//        }
//    }
//
//
//    //--- Document Fetch -------------------------------------------------------
//    private static class DocumentFetchStage
//            extends DELETE_AbstractFsImporterStage {
//        @Override
//        public boolean executeStage(FsImporterPipelineContext ctx) {
//            BaseCrawlData crawlData = ctx.getCrawlData();
//            FileDocument doc = ctx.getDocument();
//            var fileObject = ctx.getFileObject();
//            CrawlState state = ctx.getConfig().getDocumentFetcher()
//                    .fetchDocument(ctx.getFileObject(), doc);
//            crawlData.setCrawlDate(new Date());
//            crawlData.setContentType(doc.getContentType());
//            crawlData.setState(state);
//
//            if (!state.isGoodState()) {
//                String eventType;
//                if (state.isOneOf(CrawlState.NOT_FOUND)) {
//                    eventType = CrawlerEvent.REJECTED_NOTFOUND;
//                } else {
//                    eventType = CrawlerEvent.REJECTED_BAD_STATUS;
//                }
//                ctx.fireCrawlerEvent(eventType, crawlData, fileObject);
//                return false;
//            }
//            ctx.fireCrawlerEvent(CrawlerEvent.DOCUMENT_FETCHED,
//                    crawlData, fileObject);
//            return true;
//        }
//    }
}

