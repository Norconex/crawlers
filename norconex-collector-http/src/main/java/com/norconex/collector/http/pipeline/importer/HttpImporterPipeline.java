/**
 * 
 */
package com.norconex.collector.http.pipeline.importer;

import java.io.IOException;
import java.io.Reader;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.core.pipeline.importer.ImportModuleStage;
import com.norconex.collector.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.collector.core.pipeline.importer.SaveDocumentStage;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.crawler.TargetURLRedirectStrategy;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.collector.http.fetch.IHttpHeadersFetcher;
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
    
//    private static final Logger LOG = 
//            LogManager.getLogger(DocumentPipeline.class);
    
    public HttpImporterPipeline(boolean isKeepDownloads) {
        addStage(new DelayResolverStage());

        // When HTTP headers are fetched (HTTP "HEAD") before document:
        addStage(new HttpHeadersFetcherStage());
        addStage(new HttpHeadersFiltersHEADStage());
        addStage(new HttpMetadataChecksumStage(true));
        
        // HTTP "GET" and onward:
        addStage(new DocumentFetcherStage());
        if (isKeepDownloads) {
            addStage(new SaveDocumentStage());
        }
        addStage(new RobotsMetaCreateStage());
        addStage(new URLExtractorStage());
        addStage(new RobotsMetaNoIndexStage());
        addStage(new HttpHeadersFiltersGETStage());
        addStage(new HttpMetadataChecksumStage(false));
        addStage(new DocumentFiltersStage());
        addStage(new DocumentPreProcessingStage());        
        addStage(new ImportModuleStage());        
    }
    

    //--- Wait for delay to expire ---------------------------------------------
    private class DelayResolverStage extends AbstractImporterStage {
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
                    delayResolver.delay(null, ctx.getCrawlData().getReference());
                }
            }
            return true;
        }
    }

    //--- HTTP Headers Fetcher -------------------------------------------------
    private class HttpHeadersFetcherStage extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            if (!ctx.isHttpHeadFetchEnabled()) {
                return true;
            }

            HttpMetadata metadata = ctx.getMetadata();
            IHttpHeadersFetcher headersFetcher = ctx.getHttpHeadersFetcher();
            HttpCrawlData crawlData = ctx.getCrawlData();
            Properties headers = headersFetcher.fetchHTTPHeaders(
                    ctx.getHttpClient(), crawlData.getReference());
            if (headers == null) {
                crawlData.setState(HttpCrawlState.REJECTED);
                return false;
            }
            metadata.putAll(headers);
            
            ImporterPipelineUtil.enhanceHTTPHeaders(metadata);
            ImporterPipelineUtil.applyMetadataToDocument(ctx.getDocument());
            
            ctx.getCrawler().fireCrawlerEvent(
                    HttpCrawlerEvent.DOCUMENT_META_FETCHED, 
                    ctx.getCrawlData(), headersFetcher);
            return true;
        }
    }
    
    //--- HTTP Headers Filters -------------------------------------------------
    private class HttpHeadersFiltersHEADStage extends AbstractImporterStage {
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
    private class DocumentFetcherStage extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            //TODO for now we assume the document is downloadable.
            // download as file
            CrawlState state = 
                    ctx.getConfig().getHttpDocumentFetcher().fetchDocument(
                            ctx.getHttpClient(), ctx.getDocument());

            ImporterPipelineUtil.enhanceHTTPHeaders(
                    ctx.getDocument().getMetadata());
            ImporterPipelineUtil.applyMetadataToDocument(ctx.getDocument());
            
            //TODO Fix #17. Put in place a more permanent solution to this line
            TargetURLRedirectStrategy.fixRedirectURL(
                    ctx.getHttpClient(), ctx.getDocument(), 
                    ctx.getCrawlData(), ctx.getCrawlDataStore());
            //--- END Fix #17 ---
            
            if (state.isGoodState()) {
                ctx.getCrawler().fireCrawlerEvent(
                        HttpCrawlerEvent.DOCUMENT_FETCHED, ctx.getCrawlData(), 
                        ctx.getConfig().getHttpDocumentFetcher());
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
                        ctx.getConfig().getHttpDocumentFetcher());
                return false;
            }
            return true;
        }
    }

//    //--- Save Download --------------------------------------------------------            
//    private class SaveDownloadedFileStage extends AbstractImporterStage {
//        @Override
//        public boolean executeStage(HttpImporterPipelineContext ctx) {
//            if (!ctx.getConfig().isKeepDownloads()) {
//                return true;
//            }
//            
//            //TODO have an interface for how to store downloaded files
//            //(i.e., location, directory structure, file naming)
//            File workdir = ctx.getConfig().getWorkDir();
//            File downloadDir = new File(workdir, "/downloads");
//            if (!downloadDir.exists()) {
//                downloadDir.mkdirs();
//            }
//            File downloadFile = new File(downloadDir, 
//                    PathUtils.urlToPath(ctx.getCrawlData().getReference()));
//            try {
//                OutputStream out = FileUtils.openOutputStream(downloadFile);
//                IOUtils.copy(ctx.getDocument().getContent(), out);
//                IOUtils.closeQuietly(out);
//                
//                ctx.getCrawler().fireCrawlerEvent(
//                        CrawlerEvent.SAVED_FILE, ctx.getCrawlData(), 
//                        downloadFile);
//            } catch (IOException e) {
//                throw new CollectorException("Cannot create RobotsMeta for : " 
//                                + ctx.getCrawlData().getReference(), e);
//            }            
//            return true;
//        }
//    }    
    
    //--- Robots Meta Creation -------------------------------------------------
    private class RobotsMetaCreateStage extends AbstractImporterStage {
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
    private class RobotsMetaNoIndexStage extends AbstractImporterStage {
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
    private class HttpHeadersFiltersGETStage extends AbstractImporterStage {
        @Override
        public boolean executeStage(HttpImporterPipelineContext ctx) {
            if (ctx.getHttpHeadersFetcher() == null) {
                ImporterPipelineUtil.enhanceHTTPHeaders(ctx.getMetadata());
                if (ImporterPipelineUtil.isHeadersRejected(ctx)) {
                    ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
                    return false;
                }
            }
            return true;
        }
    }    
    
    
    //--- Document Pre-Processing ----------------------------------------------
    private class DocumentPreProcessingStage extends AbstractImporterStage {
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
