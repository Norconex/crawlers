/* Copyright 2010-2020 Norconex Inc.
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
package com.norconex.collector.http.pipeline.importer;

import java.io.IOException;
import java.io.Reader;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.core.pipeline.importer.DocumentFiltersStage;
import com.norconex.collector.core.pipeline.importer.ImportModuleStage;
import com.norconex.collector.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.collector.core.pipeline.importer.SaveDocumentStage;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.processor.IHttpDocumentProcessor;
import com.norconex.commons.lang.pipeline.Pipeline;

/**
 * All execution steps of a document processing from the moment it is
 * obtained from queue up to importing it.
 * @author Pascal Essiembre
 */
public class HttpImporterPipeline
        extends Pipeline<ImporterPipelineContext> {

    //TODO create a DocumentPipelinePrototype to generate prototypes
    //sharing all thread safe/common information,
    //just changing what is url/doc specific.

    public HttpImporterPipeline(boolean isKeepDownloads, boolean isOrphan) {

        // if an orphan is reprocessed, it could be that it is no longer
        // referenced because of deletion.  Because of that, we need
        // to process it again to find out.
        if (!isOrphan) {
            addStage(new RecrawlableResolverStage());
        }

        //TODO rename DelayResolver to HitInterval ??
        addStage(new DelayResolverStage());

        // When HTTP headers are fetched (HTTP "HEAD") before document:
        addStage(new HttpFetchStage(HttpMethod.HEAD));
        addStage(new MetadataFiltersStage(HttpMethod.HEAD));
        addStage(new CanonicalStage(HttpMethod.HEAD));
        addStage(new MetadataChecksumStage(HttpMethod.HEAD));

        // HTTP "GET" and onward:
        addStage(new HttpFetchStage(HttpMethod.GET));
        if (isKeepDownloads) {
            addStage(new SaveDocumentStage());
        }
        addStage(new CanonicalStage(HttpMethod.GET));
        addStage(new RobotsMetaCreateStage());
        addStage(new LinkExtractorStage());
        addStage(new RobotsMetaNoIndexStage());
        addStage(new MetadataFiltersStage(HttpMethod.GET));
        addStage(new MetadataChecksumStage(HttpMethod.GET));
        addStage(new DocumentFiltersStage());
        addStage(new DocumentPreProcessingStage());
        addStage(new ImportModuleStage());
    }

    //--- Wait for delay to expire ---------------------------------------------
    private static class DelayResolverStage extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            IDelayResolver delayResolver = ctx.getConfig().getDelayResolver();
            if (delayResolver != null) {
                if (!ctx.getConfig().isIgnoreRobotsTxt()) {
                    delayResolver.delay(
                            ctx.getConfig().getRobotsTxtProvider().getRobotsTxt(
                                    ctx.getHttpFetchClient(),
                                    ctx.getDocInfo().getReference()),
                            ctx.getDocInfo().getReference());
                } else {
                    delayResolver.delay(
                            null, ctx.getDocInfo().getReference());
                }
            }
            return true;
        }
    }

    //--- Robots Meta Creation -------------------------------------------------
    private static class RobotsMetaCreateStage extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            if (ctx.getConfig().isIgnoreRobotsMeta()) {
                return true;
            }

            try {
                Reader reader = ctx.getContentReader();
                ctx.setRobotsMeta(
                        ctx.getConfig().getRobotsMetaProvider().getRobotsMeta(
                                reader, ctx.getDocInfo().getReference(),
                                ctx.getDocument().getDocInfo().getContentType(),
                                ctx.getMetadata()));
                reader.close();

                ctx.fireCrawlerEvent(
                        HttpCrawlerEvent.CREATED_ROBOTS_META,
                        ctx.getDocInfo(),
                        ctx.getRobotsMeta());
            } catch (IOException e) {
                throw new CollectorException("Cannot create RobotsMeta for : "
                                + ctx.getDocInfo().getReference(), e);
            }
            return true;
        }
    }

    //--- Robots Meta NoIndex Check --------------------------------------------
    private static class RobotsMetaNoIndexStage extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            boolean canIndex = ctx.getConfig().isIgnoreRobotsMeta()
                    || ctx.getRobotsMeta() == null
                    || !ctx.getRobotsMeta().isNoindex();
            if (!canIndex) {

                ctx.fireCrawlerEvent(
                        HttpCrawlerEvent.REJECTED_ROBOTS_META_NOINDEX,
                        ctx.getDocInfo(),
                        ctx.getRobotsMeta());
                ctx.getDocInfo().setState(CrawlState.REJECTED);
                return false;
            }
            return canIndex;
        }
    }

    //--- Document Pre-Processing ----------------------------------------------
    private static class DocumentPreProcessingStage
            extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            if (ctx.getConfig().getPreImportProcessors() != null) {
                for (IHttpDocumentProcessor preProc :
                        ctx.getConfig().getPreImportProcessors()) {
                    preProc.processDocument(
                            ctx.getHttpFetchClient(), ctx.getDocument());
                    ctx.fireCrawlerEvent(
                            CrawlerEvent.DOCUMENT_PREIMPORTED,
                            ctx.getDocInfo(), preProc);
                }
            }
            return true;
        }
    }

}
