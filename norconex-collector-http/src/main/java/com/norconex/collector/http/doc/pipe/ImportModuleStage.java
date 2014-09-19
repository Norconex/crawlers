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
            Importer importer = ctx.getImporter();
                
            HttpDocument doc = ctx.getDocument();
            
            ImporterResponse response = importer.importDocument(
                    doc.getContent(),
                    doc.getContentType(),
                    doc.getContentEncoding(),
                    doc.getMetadata(),
                    doc.getReference());
            ctx.setImporterResponse(response);
            return true;
        }
    }