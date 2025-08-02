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
package com.norconex.crawler.beam.discovery;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.beam.frontier.FrontierUrl;
import com.norconex.crawler.beam.frontier.UrlUtil;
import com.norconex.importer.doc.Doc;

/**
 * Discovers new URLs in crawled content.
 * @author Norconex Inc.
 */
public class UrlDiscovery implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger LOG =
            LoggerFactory.getLogger(UrlDiscovery.class);

    private final DiscoveryConfig config;
    private final List<Pattern> includePatterns = new ArrayList<>();
    private final List<Pattern> excludePatterns = new ArrayList<>();

    /**
     * Creates a new URL discovery component with the specified configuration.
     * @param config the discovery configuration
     */
    public UrlDiscovery(DiscoveryConfig config) {
        this.config = config;
    }

    /**
     * Set URL include patterns.
     * @param patterns list of regex patterns to include
     */
    public void setIncludePatterns(List<String> patterns) {
        includePatterns.clear();
        if (patterns != null) {
            for (String pattern : patterns) {
                includePatterns.add(Pattern.compile(pattern));
            }
        }
    }

    /**
     * Set URL exclude patterns.
     * @param patterns list of regex patterns to exclude
     */
    public void setExcludePatterns(List<String> patterns) {
        excludePatterns.clear();
        if (patterns != null) {
            for (String pattern : patterns) {
                excludePatterns.add(Pattern.compile(pattern));
            }
        }
    }

    /**
     * Discover URLs in a document.
     * @param sourceUrl the source URL that was fetched
     * @param document the document content
     * @param tenantId the tenant ID for multi-tenant support
     * @return list of discovered URLs
     */
    public List<FrontierUrl> discoverUrls(String sourceUrl, Doc document,
            String tenantId) {
        if (sourceUrl == null || document == null) {
            return List.of();
        }

        List<FrontierUrl> discoveredUrls = new ArrayList<>();

        // Leverage Norconex Importer for HTML parsing and link extraction
        // This is a simplified implementation
        var links = extractLinks(document);

        var depth = calculateDepth(sourceUrl);
        if (depth >= config.getMaxDepth()) {
            LOG.debug("Max depth reached for URL: {}", sourceUrl);
            return List.of();
        }

        var count = 0;
        for (String link : links) {
            if (count >= config.getMaximumUrlsPerPage()) {
                LOG.debug("Maximum URLs per page ({}) reached for {}",
                        config.getMaximumUrlsPerPage(), sourceUrl);
                break;
            }

            var resolvedUrl = UrlUtil.resolveUrl(sourceUrl, link);
            if (shouldIncludeUrl(resolvedUrl, sourceUrl)) {
                var frontierUrl = FrontierUrl.createDiscovered(
                        resolvedUrl, sourceUrl, depth + 1, depth + 1, tenantId);
                discoveredUrls.add(frontierUrl);
                count++;
            }
        }

        LOG.debug("Discovered {} URLs from {}", discoveredUrls.size(),
                sourceUrl);
        return discoveredUrls;
    }

    /**
     * Create a Beam transform for URL discovery.
     * @param tenantId the tenant ID for multi-tenant support
     * @return the URL discovery transform
     */
    public PTransform<PCollection<KV<FrontierUrl, Doc>>,
            PCollection<FrontierUrl>>
            createDiscoveryTransform(String tenantId) {
        return new DiscoveryTransform(tenantId);
    }

    private boolean shouldIncludeUrl(String url, String sourceUrl) {
        // Check if it's an external URL
        if (url == null || url.isEmpty() || (!config.isIncludeExternal() && isExternal(url, sourceUrl))) {
            return false;
        }

        // Check if it's a static resource
        if (!config.isIncludeStaticResources() && isStaticResource(url)) {
            return false;
        }

        // Check if it's a media file
        if (!config.isIncludeMediaFiles() && isMediaFile(url)) {
            return false;
        }

        // Check if it contains a fragment
        if (!config.isIncludeFragments() && url.contains("#")) {
            url = url.substring(0, url.indexOf('#'));
        }

        // Check include/exclude patterns
        if (!matchesIncludePatterns(url)) {
            return false;
        }

        if (matchesExcludePatterns(url)) {
            return false;
        }

        return true;
    }

    private boolean isExternal(String url, String sourceUrl) {
        try {
            var sourceHost = new java.net.URL(sourceUrl).getHost();
            var targetHost = new java.net.URL(url).getHost();
            return !sourceHost.equals(targetHost);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isStaticResource(String url) {
        var lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".css") || lowerUrl.endsWith(".js") ||
                lowerUrl.endsWith(".png") || lowerUrl.endsWith(".jpg") ||
                lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".gif") ||
                lowerUrl.endsWith(".svg") || lowerUrl.endsWith(".ico");
    }

    private boolean isMediaFile(String url) {
        var lowerUrl = url.toLowerCase();
        return lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".mp3") ||
                lowerUrl.endsWith(".avi") || lowerUrl.endsWith(".mov") ||
                lowerUrl.endsWith(".wmv") || lowerUrl.endsWith(".flv") ||
                lowerUrl.endsWith(".ogg") || lowerUrl.endsWith(".webm");
    }

    private boolean matchesIncludePatterns(String url) {
        if (includePatterns.isEmpty()) {
            return true;
        }

        for (Pattern pattern : includePatterns) {
            if (pattern.matcher(url).matches()) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesExcludePatterns(String url) {
        for (Pattern pattern : excludePatterns) {
            if (pattern.matcher(url).matches()) {
                return true;
            }
        }

        return false;
    }

    private int calculateDepth(String url) {
        return UrlUtil.calculateDepth(url);
    }

    private List<String> extractLinks(Doc document) {
        // In a real implementation, this would use Norconex Importer
        // to parse the document and extract links
        // For this skeleton, we're returning an empty list
        return new ArrayList<>();
    }

    /**
     * Beam transform for URL discovery.
     */
    private class DiscoveryTransform extends
            PTransform<PCollection<KV<FrontierUrl, Doc>>,
                    PCollection<FrontierUrl>> {
        private static final long serialVersionUID = 1L;
        private final String tenantId;

        public DiscoveryTransform(String tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public PCollection<FrontierUrl>
                expand(PCollection<KV<FrontierUrl, Doc>> input) {
            return input.apply("DiscoverUrls", ParDo.of(
                    new DoFn<KV<FrontierUrl, Doc>, FrontierUrl>() {
                        private static final long serialVersionUID = 1L;

                        @ProcessElement
                        public void processElement(
                                @Element KV<FrontierUrl, Doc> element,
                                OutputReceiver<FrontierUrl> out) {
                            FrontierUrl url = element.getKey();
                            Doc document = element.getValue();

                            var discoveredUrls =
                                    discoverUrls(url.getUrl(), document,
                                            tenantId);

                            for (FrontierUrl discovered : discoveredUrls) {
                                out.output(discovered);
                            }
                        }
                    }));
        }
    }
}