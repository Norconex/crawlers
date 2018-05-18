/* Copyright 2010-2017 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.http.pipeline.committer;

import com.norconex.collector.core.pipeline.DocumentPipelineContext;
import com.norconex.collector.core.pipeline.committer.CommitModuleStage;
import com.norconex.collector.core.pipeline.committer.DocumentChecksumStage;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.processor.IHttpDocumentProcessor;
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
    private static class DocumentPostProcessingStage 
            extends AbstractCommitterStage {
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
}
