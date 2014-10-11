/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.pipeline.importer;

import java.io.IOException;
import java.io.Reader;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.core.pipeline.importer.DocumentFiltersStage;
import com.norconex.collector.core.pipeline.importer.ImportModuleStage;
import com.norconex.collector.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.collector.core.pipeline.importer.ImporterPipelineUtil;
import com.norconex.collector.core.pipeline.importer.SaveDocumentStage;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.crawler.TargetURLRedirectStrategy;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.collector.http.fetch.IHttpMetadataFetcher;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.pipeline.Pipeline;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpImporterPipeline 
        extends Pipeline<ImporterPipelineContext> {

    //TODO create a DocumentPipelinePrototype to generate prototypes
    //sharing all thread safe/common information, 
    //just changing what is url/doc specific.
    
    public HttpImporterPipeline(boolean isKeepDownloads) {
        addStage(new DelayResolverStage());

        // When HTTP headers are fetched (HTTP "HEAD") before document:
        addStage(new HttpMetadataFetcherStage());
        addStage(new HttpMetadataFiltersHEADStage());
        addStage(new HttpMetadataChecksumStage(true));
        
        // HTTP "GET" and onward:
        addStage(new DocumentFetcherStage());
        if (isKeepDownloads) {
            addStage(new SaveDocumentStage());
        }
        addStage(new RobotsMetaCreateStage());
        addStage(new URLExtractorStage());
        addStage(new RobotsMetaNoIndexStage());
        addStage(new HttpMetadataFiltersGETStage());
        addStage(new HttpMetadataChecksumStage(false));
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

    //--- HTTP Headers Fetcher -------------------------------------------------
    private static class HttpMetadataFetcherStage 
            extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            if (!ctx.isHttpHeadFetchEnabled()) {
                return true;
            }

            HttpMetadata metadata = ctx.getMetadata();
            IHttpMetadataFetcher headersFetcher = ctx.getHttpHeadersFetcher();
            HttpCrawlData crawlData = ctx.getCrawlData();
            Properties headers = headersFetcher.fetchHTTPHeaders(
                    ctx.getHttpClient(), crawlData.getReference());
            if (headers == null) {
                crawlData.setState(HttpCrawlState.REJECTED);
                return false;
            }
            metadata.putAll(headers);
            
            HttpImporterPipelineUtil.enhanceHTTPHeaders(metadata);
            HttpImporterPipelineUtil.applyMetadataToDocument(ctx.getDocument());
            
            ctx.getCrawler().fireCrawlerEvent(
                    HttpCrawlerEvent.DOCUMENT_METADATA_FETCHED, 
                    ctx.getCrawlData(), headersFetcher);
            return true;
        }
    }
    
    //--- HTTP Headers Filters -------------------------------------------------
    private static class HttpMetadataFiltersHEADStage 
            extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            if (ctx.getHttpHeadersFetcher() != null 
                    && ImporterPipelineUtil.isHeadersRejected(ctx)) {
                ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
                return false;
            }
            return true;
        }
    }


    
    //--- Document Fetcher -----------------------------------------------------            
    private static class DocumentFetcherStage extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            //TODO for now we assume the document is downloadable.
            // download as file
            CrawlState state = 
                    ctx.getConfig().getDocumentFetcher().fetchDocument(
                            ctx.getHttpClient(), ctx.getDocument());

            HttpImporterPipelineUtil.enhanceHTTPHeaders(
                    ctx.getDocument().getMetadata());
            HttpImporterPipelineUtil.applyMetadataToDocument(ctx.getDocument());
            
            //TODO Fix #17. Put in place a more permanent solution to this line
            TargetURLRedirectStrategy.fixRedirectURL(
                    ctx.getHttpClient(), ctx.getDocument(), 
                    ctx.getCrawlData(), ctx.getCrawlDataStore());
            //--- END Fix #17 ---
            
            if (state.isGoodState()) {
                ctx.getCrawler().fireCrawlerEvent(
                        HttpCrawlerEvent.DOCUMENT_FETCHED, ctx.getCrawlData(), 
                        ctx.getConfig().getDocumentFetcher());
            }
            ctx.getCrawlData().setState(state);
            if (!state.isGoodState()) {
                String eventType = null;
                if (state.isOneOf(HttpCrawlState.NOT_FOUND)) {
                    eventType = HttpCrawlerEvent.REJECTED_NOTFOUND;
                } else {
                    eventType = HttpCrawlerEvent.REJECTED_BAD_STATUS;
                }
                ctx.getCrawler().fireCrawlerEvent(
                        eventType, ctx.getCrawlData(), 
                        ctx.getConfig().getDocumentFetcher());
                return false;
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
                ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
                return false;
            }
            return canIndex;
        }
    }
    
    //--- Headers filters if not done already ----------------------------------
    private static class HttpMetadataFiltersGETStage 
            extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            if (ctx.getHttpHeadersFetcher() == null) {
                if (ImporterPipelineUtil.isHeadersRejected(ctx)) {
                    ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
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
                            HttpCrawlerEvent.DOCUMENT_PREIMPORTED, 
                            ctx.getCrawlData(), preProc);
                }
            }
            return true;
        }
    }    
}
