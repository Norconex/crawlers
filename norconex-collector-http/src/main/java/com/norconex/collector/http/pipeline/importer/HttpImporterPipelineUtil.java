/* Copyright 2010-2015 Norconex Inc.
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
package com.norconex.collector.http.pipeline.importer;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.collector.http.url.ICanonicalLinkDetector;
import com.norconex.commons.lang.file.ContentType;


/**
 * @author Pascal Essiembre
 *
 */
/*default*/ final class HttpImporterPipelineUtil {

    /**
     * Constructor.
     */
    private HttpImporterPipelineUtil() {
    }

    //TODO consider making public, putting content type and encoding in CORE.
    public static void applyMetadataToDocument(HttpDocument doc) {
        if (doc.getContentType() == null) {
            doc.setContentType(ContentType.valueOf(
                    doc.getMetadata().getString(
                            HttpMetadata.COLLECTOR_CONTENT_TYPE)));
            doc.setContentEncoding(doc.getMetadata().getString(
                    HttpMetadata.COLLECTOR_CONTENT_ENCODING));
        }
    }
    
    public static void enhanceHTTPHeaders(HttpMetadata metadata) {
        if (StringUtils.isNotBlank(
                metadata.getString(HttpMetadata.COLLECTOR_CONTENT_TYPE))) {
            return;
        }
        
        // Grab content type from HTTP Header
        String httpContentType = 
                metadata.getString(HttpMetadata.HTTP_CONTENT_TYPE);
        if (StringUtils.isBlank(httpContentType)) {
            for (String key : metadata.keySet()) {
                if (StringUtils.endsWith(key, HttpMetadata.HTTP_CONTENT_TYPE)) {
                    httpContentType = metadata.getString(key);
                }
            }
        }
        String contentType = StringUtils.trimToNull(
                StringUtils.substringBefore(httpContentType, ";"));

        // Grab charset form HTTP Content-Type
        String charset = null;
        if (httpContentType != null && httpContentType.contains("charset")) {
            charset = StringUtils.trimToNull(StringUtils.substringAfter(
                    httpContentType, "charset="));                
        }
        
        if (contentType != null) {
            metadata.addString(
                    HttpMetadata.COLLECTOR_CONTENT_TYPE, contentType);
        }
        if (charset != null) {
            metadata.addString(
                    HttpMetadata.COLLECTOR_CONTENT_ENCODING, charset);
        }
    }
    
    public static boolean resolveCanonical(
            HttpImporterPipelineContext ctx, boolean fromMeta) {
        
        //Return right away if canonical links are ignored or no detector.
        if (ctx.getConfig().isIgnoreCanonicalLinks()
                || ctx.getConfig().getCanonicalLinkDetector() == null) {
            return true;
        }
        
        // Proceed with metadata canonical link detection
        ICanonicalLinkDetector detector = 
                ctx.getConfig().getCanonicalLinkDetector();
        String canURL = null;
        if (fromMeta) {
            canURL = detector.detectFromMetadata(
                    ctx.getCrawlData().getReference(), ctx.getMetadata());
        } else {
            try {
                canURL = detector.detectFromContent(
                        ctx.getCrawlData().getReference(),
                        ctx.getDocument().getContent(), 
                        ctx.getDocument().getContentType());
            } catch (IOException e) {
                throw new CollectorException(
                        "Cannot resolve canonical link from content for: " 
                        + ctx.getCrawlData().getReference(), e);
            }
        }
        if (StringUtils.isNotBlank(canURL)) {
            // Call Queue pipeline on Canonical URL
            HttpCrawlData newData = (HttpCrawlData) ctx.getCrawlData().clone();
            newData.setReference(canURL);
            HttpQueuePipelineContext newContext = new HttpQueuePipelineContext(
                    ctx.getCrawler(), ctx.getCrawlDataStore(), newData);
            new HttpQueuePipeline().execute(newContext);
            ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
            ctx.getCrawler().fireCrawlerEvent(
                    HttpCrawlerEvent.REJECTED_CANONICAL, 
                    ctx.getCrawlData(), detector);
            return false;                
        }
        return true;            
    }
}
