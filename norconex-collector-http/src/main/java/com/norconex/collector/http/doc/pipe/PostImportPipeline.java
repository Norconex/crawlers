/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.pipeline.IPipelineStage;
import com.norconex.collector.core.pipeline.Pipeline;
import com.norconex.collector.http.crawler.HttpCrawlerEventFirer;
import com.norconex.collector.http.doc.IHttpDocumentProcessor;
import com.norconex.committer.ICommitter;

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
        public boolean process(PostImportPipelineContext ctx) {
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
        public boolean process(PostImportPipelineContext ctx) {
            ICommitter committer = ctx.getConfig().getCommitter();
            if (committer != null) {

                //TODO pass InputStream (or Content) instead of File?
                try {
                    File outputFile = File.createTempFile(
                            "committer-add-", ".txt", 
                            ctx.getConfig().getWorkDir());
                    
                    // Handle multi docs...
                    
                    FileUtils.copyInputStreamToFile(
                            ctx.getDocument().getContent()
                                    .getInputStream(), outputFile);
                    
                    committer.queueAdd(ctx.getDocument().getReference(), 
                            outputFile, ctx.getDocument().getMetadata());
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
