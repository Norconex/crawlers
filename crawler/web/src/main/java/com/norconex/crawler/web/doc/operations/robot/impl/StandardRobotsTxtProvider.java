/* Copyright 2010-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.robot.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.event.listeners.CrawlerLifeCycleListener;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.operations.robot.RobotsTxt;
import com.norconex.crawler.web.doc.operations.robot.RobotsTxtFilter;
import com.norconex.crawler.web.doc.operations.robot.RobotsTxtProvider;
import com.norconex.crawler.web.event.WebCrawlerEvent;
import com.norconex.crawler.web.fetch.WebFetchRequest;
import com.norconex.crawler.web.fetch.WebFetchResponse;
import com.norconex.crawler.web.fetch.HttpMethod;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Implementation of {@link RobotsTxtProvider} as per the robots.txt standard
 * described at <a href="http://www.robotstxt.org/robotstxt.html">
 * http://www.robotstxt.org/robotstxt.html</a>.
 * </p>
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class StandardRobotsTxtProvider
        extends CrawlerLifeCycleListener
        implements RobotsTxtProvider {

    @JsonIgnore
    private final Map<String, RobotsTxt> robotsTxtCache = new HashMap<>();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private CrawlContext crawler;

    @Override
    protected void onCrawlerCrawlBegin(CrawlerEvent event) {
        crawler = event.getSource();
    }

    @Override
    public synchronized RobotsTxt getRobotsTxt(
            Fetcher fetcher, String url) {
        var trimmedURL = StringUtils.trimToEmpty(url);
        var baseURL = getBaseURL(trimmedURL);
        var robotsTxt = robotsTxtCache.get(baseURL);
        if (robotsTxt != null) {
            return robotsTxt;
        }

        var robotsURL = baseURL + "/robots.txt";
        CrawlDoc doc = null;
        try {
            // Try once
            doc = new CrawlDoc(
                    new WebCrawlDocContext(robotsURL),
                    CachedInputStream.nullInputStream());
            var response = (WebFetchResponse) fetcher.fetch(
                    new WebFetchRequest(doc, HttpMethod.GET));

            //TODO handle better?

            // Try twice if redirect
            var redirURL = response.getRedirectTarget();

            if (StringUtils.isNotBlank(redirURL)) {
                LOG.debug(
                        "Fetching 'robots.txt' from redirect URL: {}",
                        redirURL);
                doc = new CrawlDoc(
                        new WebCrawlDocContext(redirURL),
                        CachedInputStream.nullInputStream());
                response = (WebFetchResponse) fetcher.fetch(
                        new WebFetchRequest(doc, HttpMethod.GET));
            }

            if (response.getStatusCode() > 0) {
                robotsTxt = parseRobotsTxt(
                        doc.getInputStream(), trimmedURL,
                        response.getUserAgent());
                LOG.debug("Fetched and parsed robots.txt: {}", robotsURL);
                if (crawler != null) {
                    crawler.fire(
                            CrawlerEvent.builder()
                                    .name(WebCrawlerEvent.FETCHED_ROBOTS_TXT)
                                    .docContext(doc.getDocContext())
                                    .source(crawler)
                                    .subject(robotsTxt)
                                    .build());
                }
            } else {
                LOG.info(
                        "No robots.txt found for {}. ({} - {})", robotsURL,
                        response.getStatusCode(), response.getReasonPhrase());
                robotsTxt = RobotsTxt.builder().build();
            }
        } catch (Exception e) {
            LOG.warn("Not able to obtain robots.txt at: {}", robotsURL, e);
            robotsTxt = RobotsTxt.builder().build();
        }
        robotsTxtCache.put(baseURL, robotsTxt);
        return robotsTxt;
    }

    protected RobotsTxt parseRobotsTxt(
            InputStream is, String url, String userAgent) throws IOException {
        var baseURL = getBaseURL(url);

        //--- Load matching data ---
        var isr =
                new InputStreamReader(is, StandardCharsets.UTF_8);
        var br = new BufferedReader(isr);
        var data = new RobotData();
        var parse = false;
        String line;
        while ((line = br.readLine()) != null) {
            line = cleanLineFromTrailingComments(line);
            if (ignoreLine(line)) {
                continue;
            }

            var key = StringUtils.substringBefore(line, ":").trim();
            var value = StringUtils.substringAfter(line, ":").trim();

            if ("sitemap".equalsIgnoreCase(key)) {
                data.sitemaps.add(value);
            }
            if ("user-agent".equalsIgnoreCase(key)) {
                // stop parsing if we have an exact match already
                if (data.precision == RobotData.Precision.EXACT) {
                    break;
                }
                var precision =
                        matchesUserAgent(userAgent, value);
                if (precision.ordinal() > data.precision.ordinal()) {
                    data.clear();
                    data.precision = precision;
                    parse = true;
                } else {
                    parse = false;
                }
            } else if (parse) {
                if ("crawl-delay".equalsIgnoreCase(key)) {
                    data.crawlDelay = value;
                } else if (StringUtils.isNotBlank(value)) {
                    data.rules.put(value, key);
                }
            }
        }
        isr.close();

        return data.toRobotsTxt(baseURL);
    }

    private String cleanLineFromTrailingComments(String line) {
        return StringUtils.substringBefore(line, "#").trim();
    }

    private static final Pattern PATTERN_COMMENT = Pattern.compile("\\s*#.*");
    private static final Pattern PATTERN_ALLOW_ALL = Pattern.compile(
            "\\s*allow\\s*:\\s*/\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_DISALLOW_NONE = Pattern.compile(
            "\\s*disallow\\s*:\\s*", Pattern.CASE_INSENSITIVE);

    private boolean ignoreLine(String line) {
        if (StringUtils.isBlank(line) || PATTERN_COMMENT.matcher(line).matches()
                || PATTERN_ALLOW_ALL.matcher(line).matches()) {
            return true;
        }
        return PATTERN_DISALLOW_NONE.matcher(line).matches();
    }

    private RobotData.Precision matchesUserAgent(
            String userAgent, String value) {
        if ("*".equals(value)) {
            return RobotData.Precision.WILD;
        }
        if (StringUtils.equalsIgnoreCase(userAgent, value)) {
            return RobotData.Precision.EXACT;
        }
        if (value.endsWith("*")) {
            var val = StringUtils.removeEnd(value, "*");
            if (StringUtils.startsWithIgnoreCase(userAgent, val)) {
                return RobotData.Precision.PARTIAL;
            }
        }
        if (StringUtils.containsIgnoreCase(userAgent, value)) {
            return RobotData.Precision.PARTIAL;
        }
        return RobotData.Precision.NOMATCH;
    }

    private String getBaseURL(String url) {
        var baseURL = HttpURL.getRoot(url);
        if (StringUtils.endsWith(baseURL, "/")) {
            baseURL = StringUtils.removeEnd(baseURL, "/");
        }
        return baseURL;
    }

    private static class RobotData {
        private enum Precision {
            NOMATCH, WILD, PARTIAL, EXACT;
        }

        private Precision precision = Precision.NOMATCH;
        private final Map<String, String> rules = new ListOrderedMap<>();
        private final List<String> sitemaps = new ArrayList<>();
        private String crawlDelay;

        private void clear() {
            rules.clear();
            crawlDelay = null;
        }

        private RobotsTxt toRobotsTxt(String baseURL) {
            List<RobotsTxtFilter> filters = new ArrayList<>();
            for (Entry<String, String> entry : rules.entrySet()) {
                var path = entry.getKey();
                var rule = entry.getValue();
                if ("disallow".equalsIgnoreCase(rule)) {
                    RobotsTxtFilter filter;
                    filter = buildURLFilter(baseURL, path, OnMatch.EXCLUDE);
                    LOG.debug("Add filter from robots.txt: {}", filter);
                    filters.add(filter);
                } else if ("allow".equalsIgnoreCase(rule)) {
                    RobotsTxtFilter filter;
                    filter = buildURLFilter(baseURL, path, OnMatch.INCLUDE);
                    LOG.debug("Add filter from robots.txt: {}", filter);
                    filters.add(filter);
                }
            }
            var delay = NumberUtils.toFloat(
                    crawlDelay, RobotsTxt.UNSPECIFIED_CRAWL_DELAY);
            return RobotsTxt
                    .builder()
                    .filters(filters)
                    .sitemapLocations(sitemaps)
                    .crawlDelay(delay)
                    .build();
        }

        private RobotsTxtFilter buildURLFilter(
                String baseURL, final String path, final OnMatch onMatch) {
            // Take the robots.txt pattern literally as it may include
            // characters (or character sequences) that would have special
            // meaning in a regular expression.
            var regex = Pattern.quote(path);

            // An asterisk within a robots.txt pattern should match any string.
            // Thus we transform it into the regular expression `.*`. As we
            // previously enclosed the pattern in \Q and \E in order for it to
            // be interpreted literally, we must also explicitly exclude the
            // `.*` from quoting.
            regex = regex.replace("*", "\\E.*\\Q");

            // A dollar sign at the end a robots.txt pattern means the same as
            // in a regular expression. That is, it matches the end of the
            // string. (Again, we take into account previous quoting).
            //
            // Note that the presence or absence of trailing slashes is ignored
            // (e.g. a path with a trailing slash also matches an anchored
            // pattern without a trailing slash).
            if (regex.endsWith("$\\E")) {
                regex = regex.replaceFirst("\\$\\\\E\\z", "\\\\E/?");
            }

            // If the robots.txt pattern was *not* anchored, we explicitly allow
            // any trailing character.
            else {
                regex += ".*";
            }

            // Last, we assemble the final regex by explicitly anchoring and
            // prepending the (quoted) baseUrl.
            regex = "\\A" + Pattern.quote(baseURL) + regex + "\\z";
            return new StdRobotsTxtFilter(path, regex, onMatch);
        }
    }

    @EqualsAndHashCode
    private static class StdRobotsTxtFilter extends GenericReferenceFilter
            implements RobotsTxtFilter {
        private final String path;

        public StdRobotsTxtFilter(
                String path, String regex, OnMatch onMatch) {
            getConfiguration()
                    .setValueMatcher(
                            TextMatcher.regex(regex).setIgnoreCase(true))
                    .setOnMatch(onMatch);
            this.path = path;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String toString() {
            return "Robots.txt -> " + (getOnMatch() == OnMatch.INCLUDE
                    ? "Allow: "
                    : "Disallow: ") + path + " (" +
                    getConfiguration().getValueMatcher().getPattern() + ")";
        }
    }
}
