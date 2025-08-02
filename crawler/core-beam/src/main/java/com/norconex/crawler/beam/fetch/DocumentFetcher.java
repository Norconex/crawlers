/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.beam.fetch;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.Serializable;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.beam.frontier.FrontierUrl;
import com.norconex.importer.doc.Doc;

/**
 * Fetches documents from URLs, leveraging Norconex Crawler functionality.
 * @author Norconex Inc.
 */
public class DocumentFetcher implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG =
            LoggerFactory.getLogger(DocumentFetcher.class);

    /**
     * Fetch a document from the given URL.
     * @param url the frontier URL to fetch
     * @return the fetched document, or null if fetch failed
     */
    public Doc fetch(FrontierUrl url) {
        if (url == null || url.getUrl() == null) {
            return null;
        }

        LOG.debug("Fetching URL: {}", url.getUrl());

        try {
            // In a real implementation, this would use Norconex Web Crawler fetching capabilities
            // For the skeleton, we return a simple document
            var doc = new Doc(url.getUrl());
            doc.getMetadata().add("crawl_timestamp",
                    String.valueOf(System.currentTimeMillis()));
            doc.getMetadata().add("url", url.getUrl());
            doc.getMetadata().add("tenant_id", url.getTenantId());

            if (url.getReferrerUrl() != null) {
                doc.getMetadata().add("referrer", url.getReferrerUrl());
            }

            // Set dummy content for demonstration purposes
            doc.setInputStream(IOUtils.toInputStream(
                    "This is a sample content for " + url.getUrl(), UTF_8));

            return doc;

        } catch (Exception e) {
            LOG.error("Error fetching URL: {}", url.getUrl(), e);
            return null;
        }
    }
}