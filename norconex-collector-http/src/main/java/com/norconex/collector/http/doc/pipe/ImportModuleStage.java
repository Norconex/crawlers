/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.pipeline.IPipelineStage;
import com.norconex.collector.http.crawler.HttpCrawlerEventFirer;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doccrawl.HttpDocCrawlState;
import com.norconex.importer.Importer;
import com.norconex.importer.response.ImporterResponse;

/*default*/ class ImportModuleStage
            implements IPipelineStage<DocumentPipelineContext> {
        
        private static final Logger LOG = 
                LogManager.getLogger(ImportModuleStage.class);
        
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            Importer importer = new Importer(
                    ctx.getConfig().getImporterConfig());
                
                
            //TODO ********************** DEAL WITH MULTI DOCS ****************
            
            HttpDocument doc = ctx.getDocument();
            
            ImporterResponse response = importer.importDocument(
                    doc.getContent().getInputStream(),
                    doc.getContentType(),
                    doc.getContentEncoding(),
                    doc.getMetadata(),
                    doc.getReference());
            ctx.setImporterResponse(response);

            
            

            
            
            //TODO how to set the document back in context so it is overwritten
            // everywhere with the returned one?
            
            
            
            
            if (response.isSuccess()) {
                // Fix #17: using crawlURL for more accurate url
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ACCEPTED document import. URL="
                            + ctx.getDocCrawl().getReference());
                }
                HttpCrawlerEventFirer.fireDocumentImported(ctx.getCrawler(),
                        ctx.getDocument());
                return true;
            }
                
                
//                if (importer.importDocument(
//                        doc.getLocalFile(),
//                        ctx.getMetadata().getContentType(),
//                        outputFile,
//                        ctx.getMetadata(),
//                        ctx.getReference().getReference())) {
//                    // Fix #17: using crawlURL for more accurate url
//                    if (LOG.isDebugEnabled()) {
//                        LOG.debug("ACCEPTED document import. URL="
//                                + doc.getUrl());
//                    }
//                    HttpCrawlerEventFirer.fireDocumentImported(ctx.getCrawler(), doc);
//                    return true;
//                }
            HttpCrawlerEventFirer.fireDocumentImportRejected(ctx.getCrawler(), 
                    ctx.getDocument());
            ctx.getDocCrawl().setState(HttpDocCrawlState.REJECTED);
            return false;
        }
    }