/**
 * 
 */
package com.norconex.collector.http.doc.pipe;

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.commons.lang.pipeline.IPipelineStage;
import com.norconex.importer.Importer;
import com.norconex.importer.response.ImporterResponse;

/*default*/ class ImportModuleStage
            implements IPipelineStage<DocumentPipelineContext> {
        
        @Override
        public boolean execute(DocumentPipelineContext ctx) {
            Importer importer = new Importer(
                    ctx.getConfig().getImporterConfig());
                
            HttpDocument doc = ctx.getDocument();
            
            ImporterResponse response = importer.importDocument(
                    doc.getContent().getInputStream(),
                    doc.getContentType(),
                    doc.getContentEncoding(),
                    doc.getMetadata(),
                    doc.getReference());
            ctx.setImporterResponse(response);
            return true;
        }
    }