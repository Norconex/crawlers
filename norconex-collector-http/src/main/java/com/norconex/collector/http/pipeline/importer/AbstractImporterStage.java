/**
 * 
 */
package com.norconex.collector.http.pipeline.importer;

import com.norconex.collector.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.commons.lang.pipeline.IPipelineStage;

/**
 * @author Pascal Essiembre
 *
 */
/*default*/ abstract class AbstractImporterStage 
        implements IPipelineStage<ImporterPipelineContext> {
    @Override
    public final boolean execute(ImporterPipelineContext context) {
        return executeStage((HttpImporterPipelineContext) context);
    }
    public abstract boolean executeStage(HttpImporterPipelineContext ctx);
}
