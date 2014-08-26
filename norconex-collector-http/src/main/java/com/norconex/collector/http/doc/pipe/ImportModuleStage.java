/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.pipeline.IPipelineStage;
import com.norconex.collector.http.crawler.HttpCrawlerEventFirer;
import com.norconex.collector.http.ref.HttpDocReferenceState;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterResponse;

/*default*/ class ImportModuleStage
            implements IPipelineStage<DocumentPipelineContext> {
        
        private static final Logger LOG = 
                LogManager.getLogger(ImportModuleStage.class);
        
        @Override
        public boolean process(DocumentPipelineContext ctx) {
            Importer importer = new Importer(
                    ctx.getConfig().getImporterConfig());
                
                
            //TODO ********************** DEAL WITH MULTI DOCS ****************
            
            ImporterResponse response = importer.importDocument(
                    ctx.getDocument().getContent().getInputStream(),
                    ctx.getDocument().getContentType(),
                    ctx.getMetadata(),
                    ctx.getReference().getReference());
            ctx.setImporterResponse(response);
            
            //TODO how to set the document back in context so it is overwritten
            // everywhere with the returned one?
            
            
            if (response.isSuccess()) {
                // Fix #17: using crawlURL for more accurate url
                if (LOG.isDebugEnabled()) {
                    LOG.debug("ACCEPTED document import. URL="
                            + ctx.getReference().getReference());
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
            ctx.getReference().setState(HttpDocReferenceState.REJECTED);
            return false;
        }
    }