/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.server.api.feature.crawl.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.norconex.crawler.web.crawler.WebCrawlerConfig.ReferencedLinkType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * A crawl sample requests.
 */
@Schema(name="CrawlSampleRequest")
@Data
public class CrawlSampleRequest {

    public enum FetcherType { GENERIC, WEBDRIVER }

    /**
     * Crawler starting URL.
     */
    private String startUrl;
    /**
     * How many milliseconds to wait between each page download attempt.
     * Default is {@value CrawlSampleRequestMapper#DEFAULT_DELAY} milliseconds.
     * Hard minimum is {@value CrawlSampleRequestMapper#HARD_MIN_DELAY}
     * milliseconds.
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private long delay = CrawlSampleRequestMapper.DEFAULT_DELAY;
    /**
     * Whether to return links found in each pages, along with these pages,
     * in a metadata field.
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private final List<ReferencedLinkType> keepReferencedLinks =
            new ArrayList<>();
    /**
     * Ignores robots rules (e.g., robots.txt, "nofollow", etc.).
     */
    private boolean ignoreRobotRules;
    /**
     * Maximum number of documents to return, default is the hard maximum:
     * {@value CrawlSampleRequestMapper#HARD_MAX_DOCS}
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private int maxDocs = CrawlSampleRequestMapper.HARD_MAX_DOCS;
    /**
     * Ignores a web site sitemap (if one exists).
     */
    private boolean ignoreSitemap;
    /**
     * The type of fetcher to use. Generic is recommended (default) unless
     * you crawl a site that relies heavily on JavaScript to generate content.
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private FetcherType fetcher = FetcherType.GENERIC;

    /**
     * List of regular expression matching URLs to include.
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private final List<String> urlIncludes = new ArrayList<>();
    /**
     * List of regular expression matching URLs to exclude.
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private final List<String> urlExcludes = new ArrayList<>();


    /**
     * List of regular expression matching document fields to include.
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private final List<String> fieldIncludes = new ArrayList<>();
    /**
     * List of regular expression matching document fields to exclude.
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private final List<String> fieldExcludes = new ArrayList<>();
    /**
     * Maximum number of characters for the returned document text content.
     * Set to <code>0</code> to disable returning content. Set to
     * <code>-1</code> for unlimited.
     * Default is {@value CrawlSampleRequestMapper#DEFAULT_MAX_CONTENT_SIZE}.
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private long maxContentSize =
            CrawlSampleRequestMapper.DEFAULT_MAX_CONTENT_SIZE;

    /**
     * Optional conditions matching the names and properties of crawler events
     * to return. Default does not return crawler events.
     */
    @JsonSetter(nulls = Nulls.SKIP)
    private final List<CrawlEventMatcher> eventMatchers =
            new ArrayList<>();
}
