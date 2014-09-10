/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.event.DocCrawlEvent;
import com.norconex.collector.http.crawler.HttpDocCrawlEvent;
import com.norconex.collector.http.crawler.TargetURLRedirectStrategy;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.collector.http.doccrawl.HttpDocCrawl;
import com.norconex.collector.http.doccrawl.HttpDocCrawlState;
import com.norconex.collector.http.fetch.IHttpHeadersFetcher;
import com.norconex.collector.http.util.PathUtils;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.pipeline.IPipelineStage;
import com.norconex.commons.lang.pipeline.Pipeline;

/**
 * @author Pascal Essiembre
 *
 */
public class DocumentPipeline extends Pipeline<DocumentPipelineContext> {

    //TODO create a DocumentPipelinePrototype to generate prototypes
    //sharing all thread safe/common information, 
    //just changing what is url/doc specific.
    
//    private static final Logger LOG = 
//            LogManager.getLogger(DocumentPipeline.class);
    
    public DocumentPipeline() {
        addStage(new DelayResolverStage());

        // When HTTP headers are fetched (HTTP "HEAD") before document:
        addStage(new HttpHeadersFetcherStage());
        addStage(new HttpHeadersFiltersHEADStage());
        addStage(new HttpHeadersChecksumStage(true));
        
        // HTTP "GET" and onward:
        addStage(new DocumentFetcherStage());
        addStage(new SaveDownloadedFileStage());
        addStage(new RobotsMetaCreateStage());
        addStage(new URLExtractorStage());
        addStage(new RobotsMetaNoIndexStage());
        addStage(new HttpHeadersFiltersGETStage());
        addStage(new HttpHeadersChecksumStage(false));
        addStage(new DocumentFiltersStage());
        addStage(new DocumentPreProcessingStage());        
        addStage(new ImportModuleStage());        
//        addStage(new HttpDocumentChecksumStage());     //TODO redo with ImporterResponse   
//        addStage(new DocumentPostProcessingStage());   //TODO redo with ImporterResponse     
//        addStage(new CommitModuleStage());             //TODO redo with ImporterResponse
    }
    
    private abstract class DocStage 
            implements IPipelineStage<DocumentPipelineContext> {
    };

    //--- Wait for delay to expire ---------------------------------------------
    private class DelayResolverStage extends DocStage {
        @Override
        public boolean execute(DocumentPipelineContext ctx) {
            IDelayResolver delayResolver = 
                    ctx.getConfig().getDelayResolver();
            if (delayResolver != null) {
                
                delayResolver.delay(
                        ctx.getConfig().getRobotsTxtProvider().getRobotsTxt(
                                ctx.getHttpClient(), 
                                ctx.getDocCrawl().getReference(), 
                                ctx.getConfig().getUserAgent()), 
                        ctx.getDocCrawl().getReference());

                
//                delayResolver.delay(
//                        ctx.getRobotsTxt(), ctx.getReference().getReference());
            }
            return true;
        }
    }

    //--- HTTP Headers Fetcher -------------------------------------------------
    private class HttpHeadersFetcherStage extends DocStage {
        @Override
        public boolean execute(DocumentPipelineContext ctx) {
            if (!ctx.isHttpHeadFetchEnabled()) {
                return true;
            }

            HttpMetadata metadata = ctx.getMetadata();
            IHttpHeadersFetcher headersFetcher = ctx.getHttpHeadersFetcher();
            HttpDocCrawl ref = ctx.getDocCrawl();
            Properties headers = headersFetcher.fetchHTTPHeaders(
                    ctx.getHttpClient(), ref.getReference());
            if (headers == null) {
                ref.setState(HttpDocCrawlState.REJECTED);
                return false;
            }
            metadata.putAll(headers);
            
            DocumentPipelineUtil.enhanceHTTPHeaders(metadata);
            DocumentPipelineUtil.applyMetadataToDocument(ctx.getDocument());
            
            ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                    DocCrawlEvent.HEADERS_FETCHED, 
                    ctx.getDocCrawl(), headersFetcher));
            return true;
        }
    }
    
    //--- HTTP Headers Filters -------------------------------------------------
    private class HttpHeadersFiltersHEADStage extends DocStage {
        @Override
        public boolean execute(DocumentPipelineContext ctx) {
            if (ctx.getHttpHeadersFetcher() != null 
                    && DocumentPipelineUtil.isHeadersRejected(ctx)) {
                ctx.getDocCrawl().setState(HttpDocCrawlState.REJECTED);
                return false;
            }
            return true;
        }
    }


    
    //--- Document Fetcher -----------------------------------------------------            
    private class DocumentFetcherStage extends DocStage {
        @Override
        public boolean execute(DocumentPipelineContext ctx) {
            //TODO for now we assume the document is downloadable.
            // download as file
            HttpDocCrawlState state = (HttpDocCrawlState) 
                    ctx.getConfig().getHttpDocumentFetcher().fetchDocument(
                            ctx.getHttpClient(), ctx.getDocument());

            DocumentPipelineUtil.enhanceHTTPHeaders(
                    ctx.getDocument().getMetadata());
            DocumentPipelineUtil.applyMetadataToDocument(ctx.getDocument());
            
            //TODO Fix #17. Put in place a more permanent solution to this line
            TargetURLRedirectStrategy.fixRedirectURL(
                    ctx.getHttpClient(), ctx.getDocument(), 
                    ctx.getDocCrawl(), ctx.getReferenceStore());
            //--- END Fix #17 ---
            
            if (state.isGoodState()) {
                ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                        DocCrawlEvent.DOCUMENT_FETCHED, ctx.getDocCrawl(), 
                        ctx.getConfig().getHttpDocumentFetcher()));
            }
            ctx.getDocCrawl().setState(state);
            if (!state.isGoodState()) {
                String eventType = null;
                if (state.isOneOf(HttpDocCrawlState.NOT_FOUND)) {
                    eventType = HttpDocCrawlEvent.REJECTED_NOTFOUND;
                } else {
                    eventType = HttpDocCrawlEvent.REJECTED_BAD_STATUS;
                }
                ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                        eventType, ctx.getDocCrawl(), 
                        ctx.getConfig().getHttpDocumentFetcher()));
                return false;
            }
            return true;
        }
    }

    //--- Save Download --------------------------------------------------------            
    private class SaveDownloadedFileStage extends DocStage {
        @Override
        public boolean execute(DocumentPipelineContext ctx) {
            if (!ctx.getConfig().isKeepDownloads()) {
                return true;
            }
            
            //TODO have an interface for how to store downloaded files
            //(i.e., location, directory structure, file naming)
            File workdir = ctx.getConfig().getWorkDir();
            File downloadDir = new File(workdir, "/downloads");
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            File downloadFile = new File(downloadDir, 
                    PathUtils.urlToPath(ctx.getDocCrawl().getReference()));
            try {
                OutputStream out = FileUtils.openOutputStream(downloadFile);
                IOUtils.copy(
                        ctx.getDocument().getContent().getInputStream(), out);
                IOUtils.closeQuietly(out);
                
                ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                        HttpDocCrawlEvent.SAVED_FILE, ctx.getDocCrawl(), 
                        downloadFile));
            } catch (IOException e) {
                throw new CollectorException("Cannot create RobotsMeta for : " 
                                + ctx.getDocCrawl().getReference(), e);
            }            
            return true;
        }
    }    
    
    //--- Robots Meta Creation -------------------------------------------------
    private class RobotsMetaCreateStage extends DocStage {
        @Override
        public boolean execute(DocumentPipelineContext ctx) {
            if (ctx.getConfig().isIgnoreRobotsMeta()) {
                return true;
            }

            try {
                Reader reader = ctx.getContentReader();
                ctx.setRobotsMeta(
                        ctx.getConfig().getRobotsMetaProvider().getRobotsMeta(
                                reader, ctx.getDocCrawl().getReference(),
                                ctx.getDocument().getContentType(),
                                ctx.getMetadata()));
                reader.close();

                ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                        HttpDocCrawlEvent.CREATED_ROBOTS_META, 
                        ctx.getDocCrawl(), 
                        ctx.getRobotsMeta()));
            } catch (IOException e) {
                throw new CollectorException("Cannot create RobotsMeta for : " 
                                + ctx.getDocCrawl().getReference(), e);
            }
            return true;
        }
    }



    
    //--- Robots Meta NoIndex Check --------------------------------------------
    private class RobotsMetaNoIndexStage extends DocStage {
        @Override
        public boolean execute(DocumentPipelineContext ctx) {
            boolean canIndex = ctx.getConfig().isIgnoreRobotsMeta() 
                    || ctx.getRobotsMeta() == null
                    || !ctx.getRobotsMeta().isNoindex();
            if (!canIndex) {
                
                ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                        HttpDocCrawlEvent.REJECTED_ROBOTS_META_NOINDEX, 
                        ctx.getDocCrawl(), 
                        ctx.getRobotsMeta()));
                ctx.getDocCrawl().setState(HttpDocCrawlState.REJECTED);
                return false;
            }
            return canIndex;
        }
    }
    
    //--- Headers filters if not done already ----------------------------------
    private class HttpHeadersFiltersGETStage extends DocStage {
        @Override
        public boolean execute(DocumentPipelineContext ctx) {
            if (ctx.getHttpHeadersFetcher() == null) {
                DocumentPipelineUtil.enhanceHTTPHeaders(ctx.getMetadata());
                if (DocumentPipelineUtil.isHeadersRejected(ctx)) {
                    ctx.getDocCrawl().setState(HttpDocCrawlState.REJECTED);
                    return false;
                }
            }
            return true;
        }
    }    
    
    
    //--- Document Pre-Processing ----------------------------------------------
    private class DocumentPreProcessingStage extends DocStage {
        @Override
        public boolean execute(DocumentPipelineContext ctx) {
            if (ctx.getConfig().getPreImportProcessors() != null) {
                for (IHttpDocumentProcessor preProc :
                        ctx.getConfig().getPreImportProcessors()) {
                    preProc.processDocument(
                            ctx.getHttpClient(), ctx.getDocument());
                    
                    ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                            HttpDocCrawlEvent.DOCUMENT_PREIMPORTED, 
                            ctx.getDocCrawl(), preProc));
                }
            }
            return true;
        }
    }    

    
    
//    //--- Document Post-Processing ---------------------------------------------
//    private class DocumentPostProcessingStage extends DocStage {
//        @Override
//        public boolean process(DocumentPipelineContext ctx) {
//            if (ctx.getConfig().getPostImportProcessors() != null) {
//                for (IHttpDocumentProcessor postProc :
//                        ctx.getConfig().getPostImportProcessors()) {
//                    postProc.processDocument(
//                            ctx.getHttpClient(), ctx.getDocument());
//                    HttpCrawlerEventFirer.fireDocumentPostProcessed(
//                            ctx.getCrawler(), ctx.getDocument(), postProc);
//                }            
//            }
//            return true;
//        }
//    }  
//    
//    //--- Document Commit ------------------------------------------------------
//    private class CommitModuleStage extends DocStage {
//        @Override
//        public boolean process(DocumentPipelineContext ctx) {
//            ICommitter committer = ctx.getConfig().getCommitter();
//            if (committer != null) {
//
//                //TODO pass InputStream (or Content) instead of File?
//                try {
//                    File outputFile = File.createTempFile(
//                            "committer-add-", ".txt", 
//                            ctx.getConfig().getWorkDir());
//                    
//                    // Handle multi docs...
//                    
//                    FileUtils.copyInputStreamToFile(
//                            ctx.getImporterResponse().getDocument().getContent()
//                                    .getInputStream(), outputFile);
//                    
//                    committer.queueAdd(ctx.getDocCrawl().getReference(), 
//                            outputFile, ctx.getMetadata());
//                } catch (IOException e) {
//                    throw new CollectorException(
//                            "Could not queue document in committer");
//                }
//            }
//            HttpCrawlerEventFirer.fireDocumentCrawled(ctx.getCrawler(), 
//                    ctx.getDocument());
//            return true;
//        }
//    }  

}
