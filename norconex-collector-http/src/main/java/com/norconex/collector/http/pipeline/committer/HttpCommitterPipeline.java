/**
 * 
 */
package com.norconex.collector.http.pipeline.committer;

import com.norconex.collector.core.pipeline.DocumentPipelineContext;
import com.norconex.collector.core.pipeline.committer.CommitModuleStage;
import com.norconex.collector.core.pipeline.committer.DocumentChecksumStage;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.commons.lang.pipeline.Pipeline;

/**
 * @author Pascal Essiembre
 *
 */
public class HttpCommitterPipeline 
        extends Pipeline<DocumentPipelineContext> {

    public HttpCommitterPipeline() {
        addStage(new DocumentChecksumStage());   
        addStage(new DocumentPostProcessingStage());     
        addStage(new CommitModuleStage());
    }
    
    //--- Document Post-Processing ---------------------------------------------
    private class DocumentPostProcessingStage extends AbstractCommitterStage {
        @Override
        public boolean executeStage(HttpCommitterPipelineContext ctx) {
            if (ctx.getConfig().getPostImportProcessors() != null) {
                for (IHttpDocumentProcessor postProc :
                        ctx.getConfig().getPostImportProcessors()) {
                    postProc.processDocument(
                            ctx.getHttpClient(), ctx.getDocument());
                    
                    ctx.getCrawler().fireCrawlerEvent(
                            HttpCrawlerEvent.DOCUMENT_POSTIMPORTED, 
                            ctx.getCrawlData(), postProc);
                }            
            }
            return true;
        }
    }  
//    
//    //--- Document Commit ------------------------------------------------------
//    private class CommitModuleStage extends AbstractCommitterStage {
//        @Override
//        public boolean executeStage(HttpCommitterPipelineContext ctx) {
//            ICommitter committer = ctx.getConfig().getCommitter();
//            if (committer != null) {
//                HttpDocument doc = ctx.getDocument();
//                committer.add(doc.getReference(), 
//                        doc.getContent(), doc.getMetadata());
//            }
//            ctx.getCrawler().fireCrawlerEvent(
//                    HttpCrawlerEvent.DOCUMENT_COMMITTED, 
//                    ctx.getCrawlData(), committer);
//            return true;
//        }
//    }  

}
