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

import org.apache.commons.collections4.queue.CircularFifoQueue;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.event.CrawlerEvent;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.core.pipeline.importer.DocumentFiltersStage;
import com.norconex.collector.core.pipeline.importer.ImportModuleStage;
import com.norconex.collector.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.collector.core.pipeline.importer.ImporterPipelineUtil;
import com.norconex.collector.core.pipeline.importer.SaveDocumentStage;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.processor.IHttpDocumentProcessor;
import com.norconex.commons.lang.pipeline.Pipeline;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpImporterPipeline
        extends Pipeline<ImporterPipelineContext> {

    //TODO Hack for ticket #533. Version 3 shall have a more robust fix.
    public static final CircularFifoQueue<String> GOOD_REDIRECTS =
            new CircularFifoQueue<>(10000);

    //TODO create a DocumentPipelinePrototype to generate prototypes
    //sharing all thread safe/common information,
    //just changing what is url/doc specific.

    public HttpImporterPipeline(boolean isKeepDownloads, boolean isOrphan, boolean isLinkExtractorQuitsAtDepth) {

        // if an orphan is reprocessed, it could be that it is no longer
        // referenced because of deletion.  Because of that, we need
        // to process it again to find out.
        if (!isOrphan) {
            addStage(new RecrawlableResolverStage());
        }

        //TODO rename DelayResolver to HitInterval ??
        addStage(new DelayResolverStage());

        // When HTTP headers are fetched (HTTP "HEAD") before document:
        addStage(new MetadataFetcherStage());
        addStage(new MetadataFiltersHEADStage());
        addStage(new MetadataCanonicalHEADStage());
        addStage(new MetadataChecksumStage(true));

        // HTTP "GET" and onward:
        addStage(new DocumentFetcherStage());
        if (isKeepDownloads) {
            addStage(new SaveDocumentStage());
        }
        addStage(new MetadataCanonicalGETStage());
        addStage(new DocumentCanonicalStage());
        addStage(new RobotsMetaCreateStage());
        addStage(new LinkExtractorStage(isLinkExtractorQuitsAtDepth));
        addStage(new RobotsMetaNoIndexStage());
        addStage(new MetadataFiltersGETStage());
        addStage(new MetadataChecksumStage(false));
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
                                    ctx.getHttpClient(),
                                    ctx.getCrawlData().getReference(),
                                    ctx.getConfig().getUserAgent()),
                            ctx.getCrawlData().getReference());
                } else {
                    delayResolver.delay(
                            null, ctx.getCrawlData().getReference());
                }
            }
            return true;
        }
    }


    //--- HTTP Headers Filters -------------------------------------------------
    private static class MetadataFiltersHEADStage
            extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            if (ctx.isHttpHeadSuccessful()
                    && ImporterPipelineUtil.isHeadersRejected(ctx)) {
                ctx.getCrawlData().setState(CrawlState.REJECTED);
                return false;
            }
            return true;
        }
    }

    //--- HTTP Headers Canonical URL handling ----------------------------------
    private static class MetadataCanonicalHEADStage
            extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            // Return right away if http headers are not fetched
            if (!ctx.isHttpHeadSuccessful()) {
                return true;
            }
            return HttpImporterPipelineUtil.resolveCanonical(ctx, true);
        }
    }

    //--- HTTP Headers Canonical URL after fetch -------------------------------
    private static class MetadataCanonicalGETStage
            extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            return HttpImporterPipelineUtil.resolveCanonical(ctx, true);
        }
    }

    //--- Document Canonical URL from <head> -----------------------------------
    private static class DocumentCanonicalStage
            extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            return HttpImporterPipelineUtil.resolveCanonical(ctx, false);
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
                                reader, ctx.getCrawlData().getReference(),
                                ctx.getDocument().getContentType(),
                                ctx.getMetadata()));
                reader.close();

                ctx.getCrawler().fireCrawlerEvent(
                        HttpCrawlerEvent.CREATED_ROBOTS_META,
                        ctx.getCrawlData(),
                        ctx.getRobotsMeta());
            } catch (IOException e) {
                throw new CollectorException("Cannot create RobotsMeta for : "
                                + ctx.getCrawlData().getReference(), e);
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

                ctx.getCrawler().fireCrawlerEvent(
                        HttpCrawlerEvent.REJECTED_ROBOTS_META_NOINDEX,
                        ctx.getCrawlData(),
                        ctx.getRobotsMeta());
                ctx.getCrawlData().setState(CrawlState.REJECTED);
                return false;
            }
            return canIndex;
        }
    }

    //--- Headers filters if not done already ----------------------------------
    private static class MetadataFiltersGETStage
            extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            if (!ctx.isHttpHeadSuccessful()) {
                if (ImporterPipelineUtil.isHeadersRejected(ctx)) {
                    ctx.getCrawlData().setState(CrawlState.REJECTED);
                    return false;
                }
            }
            return true;
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
                            ctx.getHttpClient(), ctx.getDocument());

                    ctx.getCrawler().fireCrawlerEvent(
                            CrawlerEvent.DOCUMENT_PREIMPORTED,
                            ctx.getCrawlData(), preProc);
                }
            }
            return true;
        }
    }
}
