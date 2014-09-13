/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import com.norconex.collector.core.crawler.event.DocCrawlEvent;
import com.norconex.collector.http.crawler.HttpDocCrawlEvent;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.committer.ICommitter;
import com.norconex.commons.lang.pipeline.IPipelineStage;
import com.norconex.commons.lang.pipeline.Pipeline;

/**
 * @author Pascal Essiembre
 *
 */
public class PostImportPipeline 
        extends Pipeline<PostImportPipelineContext> {

    public PostImportPipeline() {
        addStage(new DocumentChecksumStage());   
        addStage(new DocumentPostProcessingStage());     
        addStage(new CommitModuleStage());
    }
    
    private abstract class DocStage 
            implements IPipelineStage<PostImportPipelineContext> {
    };

    //--- Document Post-Processing ---------------------------------------------
    private class DocumentPostProcessingStage extends DocStage {
        @Override
        public boolean execute(PostImportPipelineContext ctx) {
            if (ctx.getConfig().getPostImportProcessors() != null) {
                for (IHttpDocumentProcessor postProc :
                        ctx.getConfig().getPostImportProcessors()) {
                    postProc.processDocument(
                            ctx.getHttpClient(), ctx.getDocument());
                    
                    ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                            HttpDocCrawlEvent.DOCUMENT_POSTIMPORTED, 
                            ctx.getDocCrawl(), postProc));
                }            
            }
            return true;
        }
    }  
    
    //--- Document Commit ------------------------------------------------------
    private class CommitModuleStage extends DocStage {
        @Override
        public boolean execute(PostImportPipelineContext ctx) {
            ICommitter committer = ctx.getConfig().getCommitter();
            if (committer != null) {
                HttpDocument doc = ctx.getDocument();
                committer.add(doc.getReference(), 
                        doc.getContent().getInputStream(), doc.getMetadata());
            }
            ctx.getCrawler().fireDocCrawlEvent(new DocCrawlEvent(
                    HttpDocCrawlEvent.DOCUMENT_COMMITTED, 
                    ctx.getDocCrawl(), committer));
            return true;
        }
    }  

}
