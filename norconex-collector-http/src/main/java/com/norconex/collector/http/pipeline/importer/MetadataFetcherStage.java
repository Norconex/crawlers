/* Copyright 2016-2020 Norconex Inc.
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

import java.nio.charset.UnsupportedCharsetException;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.crawler.event.CrawlerEvent;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.fetch.IHttpMetadataFetcher;
import com.norconex.collector.http.redirect.RedirectStrategyWrapper;
import com.norconex.commons.lang.map.Properties;

/**
 * <p>Fetches a document metadata (i.e. HTTP headers).</p>
 * <p>Prior to 2.6.0, the code for this class was part of
 * {@link HttpImporterPipeline}.
 * @author Pascal Essiembre
 * @since 2.6.0
 */
/*default*/ class MetadataFetcherStage extends AbstractImporterStage {

    private static final Logger LOG = LogManager.getLogger(
            MetadataFetcherStage.class);

    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        if (!ctx.isHttpHeadFetchEnabled()) {
            return true;
        }

        HttpCrawlData crawlData = ctx.getCrawlData();

        IHttpMetadataFetcher headersFetcher = ctx.getHttpHeadersFetcher();

        HttpMetadata metadata = ctx.getMetadata();
        Properties headers = new Properties(metadata.isCaseInsensitiveKeys());

        HttpFetchResponse response = headersFetcher.fetchHTTPHeaders(
                ctx.getHttpClient(), crawlData.getReference(), headers);

        metadata.putAll(headers);

        try {
            HttpImporterPipelineUtil.enhanceHTTPHeaders(metadata);
        } catch (UnsupportedCharsetException e) {
            LOG.warn("Unsupported character encoding \""
                    + e.getCharsetName() + "\" defined in \"Content-Type\" "
                    + "HTTP response header. Detection will be attempted "
                    + "instead for \"" + ctx.getDocument().getReference()
                    + "\".");
        }
        HttpImporterPipelineUtil.applyMetadataToDocument(ctx.getDocument());

        CrawlState state = response.getCrawlState();
        crawlData.setState(state);

        // if not good or not found, consider it bad
        boolean isBadStatus = !state.isGoodState()
                && !state.isOneOf(CrawlState.NOT_FOUND);

        //-- Deal with redirects ---
        if (!isBadStatus || !ctx.getConfig().isSkipMetaFetcherOnBadStatus()) {
            String redirectURL = RedirectStrategyWrapper.getRedirectURL();
            if (StringUtils.isNotBlank(redirectURL)) {
                HttpImporterPipelineUtil.queueRedirectURL(
                        ctx, response, redirectURL);
                return false;
            }
        }

        // Good state
        if (state.isGoodState()) {
            ctx.fireCrawlerEvent(CrawlerEvent.DOCUMENT_METADATA_FETCHED,
                    crawlData, response);
            ctx.setHttpHeadSuccessful(true);
            return true;
        }

        // Bad state
        String eventType = null;
        if (state.isOneOf(CrawlState.NOT_FOUND)) {
            eventType = CrawlerEvent.REJECTED_NOTFOUND;
        } else {
            eventType = CrawlerEvent.REJECTED_BAD_STATUS;
            // A bad status could be happening only on HEAD requests
            // so we optionally skip fetching instead of rejecting the document
            if (ctx.getConfig().isSkipMetaFetcherOnBadStatus()) {
                LOG.warn("Bad HTTP response when fetching metadata: "
                        + response);
                return true;
            }
        }
        ctx.fireCrawlerEvent(eventType, crawlData, response);
        return false;
    }
}