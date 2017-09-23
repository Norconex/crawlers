/* Copyright 2010-2016 Norconex Inc.
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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.collector.http.url.ICanonicalLinkDetector;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.commons.lang.file.ContentType;


/**
 * @author Pascal Essiembre
 *
 */
/*default*/ final class HttpImporterPipelineUtil {

    private static final Logger LOG = 
            LogManager.getLogger(HttpImporterPipelineUtil.class);
    
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
        String collectorContentType = 
                metadata.getString(HttpMetadata.COLLECTOR_CONTENT_TYPE);
        String collectorContentEncoding = 
                metadata.getString(HttpMetadata.COLLECTOR_CONTENT_ENCODING);
        
        if (StringUtils.isNotBlank(collectorContentType)
                && StringUtils.isNotBlank(collectorContentEncoding)) {
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
        
        if (StringUtils.isBlank(collectorContentType)) {
            String contentType = StringUtils.trimToNull(
                    StringUtils.substringBefore(httpContentType, ";"));
            if (contentType != null) {
                metadata.addString(
                        HttpMetadata.COLLECTOR_CONTENT_TYPE, contentType);
            }
        }
        
        if (StringUtils.isBlank(collectorContentEncoding)) {
            // Grab charset form HTTP Content-Type
            String charset = null;
            if (httpContentType != null 
                    && httpContentType.contains("charset")) {
                charset = StringUtils.trimToNull(StringUtils.substringAfter(
                        httpContentType, "charset="));                
            }
            
            if (charset != null) {
                metadata.addString(
                        HttpMetadata.COLLECTOR_CONTENT_ENCODING, charset);
            }
        }
    }
    
    // return true if we process this doc, false if we don't because we 
    // will use a canonical URL instead
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
            String referrerURL = ctx.getCrawlData().getReference();
            
            // Since the current/containing page URL has already been 
            // normalized, make sure we normalize this one for the purpose
            // of comparing it.  It will them be sent un-normalized to 
            // the queue pipeline, since that pipeline performs the 
            // normalization after a few other steps.
            String normalizedCanURL = canURL;
            IURLNormalizer normalizer = ctx.getConfig().getUrlNormalizer();
            if (normalizer != null) {
                normalizedCanURL = normalizer.normalizeURL(normalizedCanURL);
            }
            if (normalizedCanURL == null) {
                LOG.info("Canonical URL detected is null after "
                      + "normalization so it will be ignored and its referrer "
                      + "will be processed instead.  Canonical URL: \""
                      +  canURL + "\" Rererrer URL: " + referrerURL);
                return false;
            }
            
            if (normalizedCanURL.equals(referrerURL)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Canonical URL detected is the same as document "
                          + "URL. Process normally. URL: " + referrerURL);
                }
                return true;
            }

            // Call Queue pipeline on Canonical URL
            if (LOG.isDebugEnabled()) {
                LOG.debug("Canonical URL detected is different than document "
                      + "URL. Document will be rejected while canonical URL "
                      + "will be queued for processing: " + canURL);
            }
            HttpCrawlData newData = (HttpCrawlData) ctx.getCrawlData().clone();
            newData.setReference(canURL);
            newData.setReferrerReference(referrerURL);
            
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
    
    // Keep this method static so multi-threads treat this method as one
    // instance (to avoid redirect dups).
    public static synchronized void queueRedirectURL(
            HttpImporterPipelineContext ctx, 
            HttpFetchResponse response,
            String redirectURL) {
        ICrawlDataStore store = ctx.getCrawlDataStore();
        HttpCrawlData crawlData = ctx.getCrawlData();            
        String sourceURL =  crawlData.getReference();
        
        //--- Do not queue if previously handled ---
        //TODO throw an event if already active/processed(ing)?
        if (store.isActive(redirectURL)) {
            rejectRedirectDup("being processed", sourceURL, redirectURL);
            return;
        } else if (store.isQueued(redirectURL)) {
            rejectRedirectDup("queued", sourceURL, redirectURL);
            return;
        } else if (store.isProcessed(redirectURL)) {
            rejectRedirectDup("processed", sourceURL, redirectURL);
            return;
        }

        //--- Fresh URL, queue it! ---
        crawlData.setState(HttpCrawlState.REDIRECT);
        HttpFetchResponse newResponse = new HttpFetchResponse(
                HttpCrawlState.REDIRECT, 
                response.getStatusCode(),
                response.getReasonPhrase() + " (" + redirectURL + ")");
        ctx.fireCrawlerEvent(HttpCrawlerEvent.REJECTED_REDIRECTED, 
                crawlData, newResponse);
        
        HttpCrawlData newData = new HttpCrawlData(
                redirectURL, crawlData.getDepth());
        newData.setReferrerReference(crawlData.getReferrerReference());
        newData.setReferrerLinkTag(crawlData.getReferrerLinkTag());
        newData.setReferrerLinkText(crawlData.getReferrerLinkText());
        newData.setReferrerLinkTitle(crawlData.getReferrerLinkTitle());
        newData.setRedirectTrail(
                ArrayUtils.add(crawlData.getRedirectTrail(), sourceURL));
        if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                crawlData.getReference(), redirectURL)) {
            HttpQueuePipelineContext newContext = 
                    new HttpQueuePipelineContext(
                            ctx.getCrawler(), store, newData);
            new HttpQueuePipeline().execute(newContext);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("URL redirect target not in scope: " + redirectURL);
            }
            newData.setState(HttpCrawlState.REJECTED);
            ctx.fireCrawlerEvent(
                    HttpCrawlerEvent.REJECTED_FILTER, newData, 
                    ctx.getConfig().getURLCrawlScopeStrategy());
        }
    }
    
    private static void rejectRedirectDup(String action, 
            String originalURL, String redirectURL) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Redirect target URL is already " + action
                    + ": " + redirectURL + " (from: " + originalURL + ").");
        }
    }
}
