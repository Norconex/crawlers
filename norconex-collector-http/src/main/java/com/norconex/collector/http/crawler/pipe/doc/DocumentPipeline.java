/**
 * 
 */
package com.norconex.collector.http.crawler.pipe.doc;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import org.apache.commons.io.FileUtils;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.pipeline.IPipelineStage;
import com.norconex.collector.core.pipeline.Pipeline;
import com.norconex.collector.core.ref.ReferenceState;
import com.norconex.collector.http.crawler.HttpCrawlerEventFirer;
import com.norconex.collector.http.crawler.HttpDocReference;
import com.norconex.collector.http.crawler.HttpDocReferenceState;
import com.norconex.collector.http.crawler.TargetURLRedirectStrategy;
import com.norconex.collector.http.delay.IDelayResolver;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.collector.http.fetch.IHttpHeadersFetcher;
import com.norconex.committer.ICommitter;
import com.norconex.commons.lang.map.Properties;

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
        addStage(new RobotsMetaCreateStage());
        addStage(new URLExtractorStage());
        addStage(new RobotsMetaNoIndexStage());
        addStage(new HttpHeadersFiltersGETStage());
        addStage(new HttpHeadersChecksumStage(false));
        addStage(new DocumentFiltersStage());
        addStage(new DocumentPreProcessingStage());        
        addStage(new ImportModuleStage());        
        addStage(new HttpDocumentChecksumStage());        
        addStage(new DocumentPostProcessingStage());        
        addStage(new CommitModuleStage());        
        

    }
    
    private abstract class DocStage 
            implements IPipelineStage<DocumentPipelineContext> {
    };

    //--- Wait for delay to expire ---------------------------------------------
    private class DelayResolverStage extends DocStage {
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            IDelayResolver delayResolver = 
                    ctx.getConfig().getDelayResolver();
            if (delayResolver != null) {
                
                delayResolver.delay(
                        ctx.getConfig().getRobotsTxtProvider().getRobotsTxt(
                                ctx.getHttpClient(), 
                                ctx.getReference().getReference(), 
                                ctx.getConfig().getUserAgent()), 
                        ctx.getReference().getReference());

                
//                delayResolver.delay(
//                        ctx.getRobotsTxt(), ctx.getReference().getReference());
            }
            return true;
        }
    }

    //--- HTTP Headers Fetcher -------------------------------------------------
    private class HttpHeadersFetcherStage extends DocStage {
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            if (!ctx.isHttpHeadFetchEnabled()) {
                return true;
            }

            HttpMetadata metadata = ctx.getMetadata();
            IHttpHeadersFetcher headersFetcher = ctx.getHttpHeadersFetcher();
            HttpDocReference ref = ctx.getReference();
            Properties headers = headersFetcher.fetchHTTPHeaders(
                    ctx.getHttpClient(), ref.getReference());
            if (headers == null) {
                ref.setState(HttpDocReferenceState.REJECTED);
                return false;
            }
            metadata.putAll(headers);
            DocumentPipelineUtil.enhanceHTTPHeaders(metadata);
            HttpCrawlerEventFirer.fireDocumentHeadersFetched(
                    ctx.getCrawler(), ref.getReference(), 
                    headersFetcher, metadata);
            return true;
        }
    }
    
    //--- HTTP Headers Filters -------------------------------------------------
    private class HttpHeadersFiltersHEADStage extends DocStage {
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            if (ctx.getHttpHeadersFetcher() != null 
                    && DocumentPipelineUtil.isHeadersRejected(ctx)) {
                ctx.getReference().setState(HttpDocReferenceState.REJECTED);
                return false;
            }
            return true;
        }
    }


    
    //--- Document Fetcher -----------------------------------------------------            
    private class DocumentFetcherStage extends DocStage {
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            //TODO for now we assume the document is downloadable.
            // download as file
            ReferenceState state = ctx.getConfig().getHttpDocumentFetcher()
                    .fetchDocument(ctx.getHttpClient(), ctx.getDocument());
            

            //TODO Fix #17. Put in place a more permanent solution to this line
            TargetURLRedirectStrategy.fixRedirectURL(
                    ctx.getHttpClient(), ctx.getDocument(), 
                    ctx.getReference(), ctx.getReferenceStore());
            //--- END Fix #17 ---
            
            
//            if (status == HttpDocReferenceState.OK) {
            if (state.isValid()) {
                HttpCrawlerEventFirer.fireDocumentFetched(
                        ctx.getCrawler(), ctx.getDocument(), 
                        ctx.getConfig().getHttpDocumentFetcher());
            }
            ctx.getReference().setState(state);
            if (!ctx.getReference().getState().isValid()) {
//            if (ctx.getReference().getState() != HttpDocReferenceState.OK) {
                return false;
            }
            return true;
        }
    }
    
    //--- Robots Meta Creation -------------------------------------------------
    private class RobotsMetaCreateStage extends DocStage {
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            if (ctx.getConfig().isIgnoreRobotsMeta()) {
                return true;
            }

            try {
                Reader reader = ctx.getContentReader();
                ctx.setRobotsMeta(
                        ctx.getConfig().getRobotsMetaProvider().getRobotsMeta(
                                reader, ctx.getReference().getReference(),
                                ctx.getMetadata().getContentType(),
                                ctx.getMetadata()));
                reader.close();
            } catch (IOException e) {
                throw new CollectorException("Cannot create RobotsMeta for : " 
                                + ctx.getReference().getReference(), e);
            }
            return true;
        }
    }



    
    //--- Robots Meta NoIndex Check --------------------------------------------
    private class RobotsMetaNoIndexStage extends DocStage {
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            boolean canIndex = ctx.getConfig().isIgnoreRobotsMeta() 
                    || ctx.getRobotsMeta() == null
                    || !ctx.getRobotsMeta().isNoindex();
            if (!canIndex) {
                HttpCrawlerEventFirer.fireDocumentRobotsMetaRejected(
                        ctx.getCrawler(), ctx.getReference().getReference(), 
                        ctx.getRobotsMeta());
                ctx.getReference().setState(HttpDocReferenceState.REJECTED);
                return false;
            }
            return canIndex;
        }
    }
    
    //--- Headers filters if not done already ----------------------------------
    private class HttpHeadersFiltersGETStage extends DocStage {
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            if (ctx.getHttpHeadersFetcher() == null) {
                DocumentPipelineUtil.enhanceHTTPHeaders(ctx.getMetadata());
                if (DocumentPipelineUtil.isHeadersRejected(ctx)) {
                    ctx.getReference().setState(HttpDocReferenceState.REJECTED);
                    return false;
                }
            }
            return true;
        }
    }    
    
    
    //--- Document Pre-Processing ----------------------------------------------
    private class DocumentPreProcessingStage extends DocStage {
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            if (ctx.getConfig().getPreImportProcessors() != null) {
                for (IHttpDocumentProcessor preProc :
                        ctx.getConfig().getPreImportProcessors()) {
                    preProc.processDocument(
                            ctx.getHttpClient(), ctx.getDocument());
                    HttpCrawlerEventFirer.fireDocumentPreProcessed(
                            ctx.getCrawler(), ctx.getDocument(), preProc);
                }
            }
            return true;
        }
    }    

    
    
    //--- Document Post-Processing ---------------------------------------------
    private class DocumentPostProcessingStage extends DocStage {
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            if (ctx.getConfig().getPostImportProcessors() != null) {
                for (IHttpDocumentProcessor postProc :
                        ctx.getConfig().getPostImportProcessors()) {
                    postProc.processDocument(
                            ctx.getHttpClient(), ctx.getDocument());
                    HttpCrawlerEventFirer.fireDocumentPostProcessed(
                            ctx.getCrawler(), ctx.getDocument(), postProc);
                }            
            }
            return true;
        }
    }  
    
    //--- Document Commit ------------------------------------------------------
    private class CommitModuleStage extends DocStage {
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            ICommitter committer = ctx.getConfig().getCommitter();
            if (committer != null) {

                //TODO pass InputStream (or Content) instead of File?
                try {
                    File outputFile = File.createTempFile(
                            "committer-add-", ".txt", 
                            ctx.getConfig().getWorkDir());
                    FileUtils.copyInputStreamToFile(
                            ctx.getContent().getInputStream(), outputFile);
                    
                    committer.queueAdd(ctx.getReference().getReference(), 
                            outputFile, ctx.getMetadata());
                } catch (IOException e) {
                    throw new CollectorException(
                            "Could not queue document in committer");
                }
            }
            HttpCrawlerEventFirer.fireDocumentCrawled(ctx.getCrawler(), 
                    ctx.getDocument());
            return true;
        }
    }  

    

}
