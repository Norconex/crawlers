/* Copyright 2015-2020 Norconex Inc.
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
import java.nio.charset.UnsupportedCharsetException;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.core.doc.CollectorMetadata;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.redirect.RedirectStrategyWrapper;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.util.CharsetUtil;

/**
 * <p>Fetches (i.e. download for processing) a document.</p>
 * <p>Prior to 2.3.0, the code for this class was part of
 * {@link HttpImporterPipeline}.
 * @author Pascal Essiembre
 * @since 2.3.0
 */
/*default*/ class DocumentFetcherStage extends AbstractImporterStage {

    private static final Logger LOG =
            LogManager.getLogger(DocumentFetcherStage.class);

    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        HttpCrawlData crawlData = ctx.getCrawlData();

        HttpFetchResponse response =
                ctx.getConfig().getDocumentFetcher().fetchDocument(
                        ctx.getHttpClient(), ctx.getDocument());

        crawlData.setCrawlDate(new Date());

        try {
            HttpImporterPipelineUtil.enhanceHTTPHeaders(
                    ctx.getDocument().getMetadata());
        } catch (UnsupportedCharsetException e) {
            LOG.warn("Unsupported character encoding \""
                    + e.getCharsetName() + "\" defined in \"Content-Type\" "
                    + "HTTP response header. Detection will be attempted "
                    + "instead for \"" + ctx.getDocument().getReference()
                    + "\".");
            try {
                String ct = new ContentTypeDetector().detect(
                        ctx.getContent()).toString();
                String ce = CharsetUtil.detectCharset(ctx.getContent(), null);
                ctx.getMetadata().setString(
                        CollectorMetadata.COLLECTOR_CONTENT_TYPE, ct);
                ctx.getMetadata().setString(
                        CollectorMetadata.COLLECTOR_CONTENT_ENCODING, ce);
                LOG.info("Detected '" + ct + "' and '" + ce + "' as the "
                        + "content type and character encoding for \""
                        + ctx.getDocument().getReference() + "\".");

            } catch (IOException e1) {
                LOG.warn("Could not detect content type and character "
                        + "encoding from content for \""
                        + ctx.getDocument().getReference() + "\".");
            }
        }

        HttpImporterPipelineUtil.applyMetadataToDocument(ctx.getDocument());

        crawlData.setContentType(ctx.getDocument().getContentType());

        //-- Deal with redirects ---
        String redirectURL = RedirectStrategyWrapper.getRedirectURL();
        if (StringUtils.isNotBlank(redirectURL)) {
            HttpImporterPipelineUtil.queueRedirectURL(
                    ctx, response, redirectURL);
            return false;
        }

        CrawlState state = response.getCrawlState();
        crawlData.setState(state);
        if (state.isGoodState()) {
            ctx.fireCrawlerEvent(HttpCrawlerEvent.DOCUMENT_FETCHED,
                    crawlData, response);
        } else {
            String eventType = null;
            if (state.isOneOf(HttpCrawlState.NOT_FOUND)) {
                eventType = HttpCrawlerEvent.REJECTED_NOTFOUND;
            } else {
                eventType = HttpCrawlerEvent.REJECTED_BAD_STATUS;
            }
            ctx.fireCrawlerEvent(eventType, crawlData, response);
            return false;
        }
        return true;
    }
}