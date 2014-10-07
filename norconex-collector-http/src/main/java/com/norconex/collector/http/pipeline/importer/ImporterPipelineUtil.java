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

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.importer.handler.filter.IOnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;

/**
 * @author Pascal Essiembre
 *
 */
/*default*/ final class ImporterPipelineUtil {

    private static final Logger LOG = 
            LogManager.getLogger(ImporterPipelineUtil.class);
    
    /**
     * Constructor.
     */
    private ImporterPipelineUtil() {
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
        
        String contentType = metadata.getString(HttpMetadata.HTTP_CONTENT_TYPE);
        if (StringUtils.isBlank(contentType)) {
            for (String key : metadata.keySet()) {
                if (StringUtils.endsWith(key, HttpMetadata.HTTP_CONTENT_TYPE)) {
                    contentType = metadata.getString(key);
                }
            }
        }
        if (StringUtils.isNotBlank(contentType)) {
            String mimeType = contentType.replaceFirst("(.*?)(;.*)", "$1");
            String charset = contentType.replaceFirst("(.*?)(; )(.*)", "$3");
            charset = charset.replaceFirst("(charset=)(.*)", "$2");
            metadata.addString(HttpMetadata.COLLECTOR_CONTENT_TYPE, mimeType);
            metadata.addString(HttpMetadata.COLLECTOR_CONTENT_ENCODING, charset);
        }
    }

    public static boolean isHeadersRejected(HttpImporterPipelineContext ctx) {
        IHttpHeadersFilter[] filters = ctx.getConfig().getHttpHeadersFilters();
        if (filters == null) {
            return false;
        }
        HttpMetadata headers = ctx.getMetadata();
        boolean hasIncludes = false;
        boolean atLeastOneIncludeMatch = false;
        for (IHttpHeadersFilter filter : filters) {
            boolean accepted = filter.acceptDocument(
                    ctx.getCrawlData().getReference(), headers);
            boolean isInclude = filter instanceof IOnMatchFilter
                   && OnMatch.INCLUDE == ((IOnMatchFilter) filter).getOnMatch();
            if (isInclude) {
                hasIncludes = true;
                if (accepted) {
                    atLeastOneIncludeMatch = true;
                }
                continue;
            }
            if (accepted) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format(
                            "ACCEPTED document http headers. URL=%s Filter=%s",
                            ctx.getCrawlData().getReference(), filter));
                }
            } else {
                ctx.getCrawler().fireCrawlerEvent(
                        HttpCrawlerEvent.REJECTED_FILTER, 
                        ctx.getCrawlData(), filter);
                return true;
            }
        }
        if (hasIncludes && !atLeastOneIncludeMatch) {
            ctx.getCrawler().fireCrawlerEvent(
                    HttpCrawlerEvent.REJECTED_FILTER, ctx.getCrawlData(), null);
            return true;
        }
        return false;        
    }

}
