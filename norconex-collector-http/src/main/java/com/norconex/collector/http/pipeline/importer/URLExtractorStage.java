/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.pipeline.importer;

import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.collector.http.url.IURLExtractor;

/**
 * Extract URLs before sending to importer (because the importer may
 * strip some "valid" urls in producing content-centric material.
 * Plus, any additional urls could be added to Metadata and they will
 * be considered.
 */
/*default*/ class URLExtractorStage extends AbstractImporterStage {

    private static final Logger LOG = LogManager
            .getLogger(URLExtractorStage.class);
    
    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        if (ctx.getRobotsMeta() != null 
                && ctx.getRobotsMeta().isNofollow()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No URLs extracted due to Robots nofollow rule "
                        + "for URL: " + ctx.getCrawlData().getReference());
            }
            return true;
        }
        
        Set<String> urls = null;
        try {
            Reader reader = ctx.getContentReader();
            
            IURLExtractor urlExtractor = ctx.getConfig().getUrlExtractor();
            urls = urlExtractor.extractURLs(
                    reader, ctx.getCrawlData().getReference(), 
                    ctx.getDocument().getContentType());
            IOUtils.closeQuietly(reader);
        } catch (IOException e) {
            throw new CollectorException("Cannot extract URLs from: " 
                    + ctx.getCrawlData().getReference(), e);
        }

        Set<String> uniqueURLs = new HashSet<String>();
        if (urls != null) {
            for (String url : urls) {
                HttpCrawlData newURL = new HttpCrawlData(
                        url, ctx.getCrawlData().getDepth() + 1);
                HttpQueuePipelineContext newContext = 
                        new HttpQueuePipelineContext(
                                ctx.getCrawler(), ctx.getCrawlDataStore(), 
                                newURL);
                //TODO do we want to capture them all or just the valid ones?
                new HttpQueuePipeline().execute(newContext);
                uniqueURLs.add(newURL.getReference());
            }
        }
        if (!uniqueURLs.isEmpty()) {
            ctx.getMetadata().addString(HttpMetadata.COLLECTOR_REFERNCED_URLS, 
                    uniqueURLs.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
        }
        
        ctx.getCrawler().fireCrawlerEvent(
                HttpCrawlerEvent.URLS_EXTRACTED, ctx.getCrawlData(), uniqueURLs);
        return true;
    }
}