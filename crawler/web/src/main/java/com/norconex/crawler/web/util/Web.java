/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.util;

import static org.apache.commons.lang3.StringUtils.substring;

import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.storage.GridMap;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.event.WebCrawlerEvent;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.operations.robot.RobotsTxt;
import com.norconex.crawler.web.operations.scope.UrlScope;

import lombok.NonNull;

public final class Web {

    private Web() {
    }

    public static <T> GridMap<T> gridCache(
            @NonNull CrawlerContext crawlerContext,
            @NonNull String name,
            @NonNull Class<? extends T> type) {
        return crawlerContext.getGrid().storage().getMap(name, type);
    }

    public static void fireIfUrlOutOfScope(
            CrawlerContext crawler,
            WebCrawlDocContext docContext,
            UrlScope urlScope) {
        if (!urlScope.isInScope()) {
            crawler.fire(CrawlerEvent
                    .builder()
                    .name(WebCrawlerEvent.REJECTED_OUT_OF_SCOPE)
                    .source(crawler)
                    .subject(Web.config(crawler).getUrlScopeResolver())
                    .docContext(docContext)
                    .message(urlScope.outOfScopeReason())
                    .build());
        }
    }

    public static WebCrawlerConfig config(CrawlerContext crawlerContext) {
        return (WebCrawlerConfig) crawlerContext.getConfiguration();
    }

    public static HttpFetcher fetcher(CrawlerContext crawlerContext) {
        return (HttpFetcher) crawlerContext.getFetcher();
    }

    public static WebCrawlDocContext docContext(@NonNull CrawlDoc crawlDoc) {
        return (WebCrawlDocContext) crawlDoc.getDocContext();
    }

    public static WebCrawlDocContext cachedDocContext(
            @NonNull CrawlDoc crawlDoc) {
        return (WebCrawlDocContext) crawlDoc.getCachedDocContext();
    }

    public static RobotsTxt robotsTxt(CrawlerContext crawler,
            String reference) {
        var cfg = Web.config(crawler);
        return Optional.ofNullable(cfg.getRobotsTxtProvider())
                .map(rb -> rb.getRobotsTxt(
                        (HttpFetcher) crawler.getFetcher(),
                        reference))
                .orElse(null);
    }

    //TODO Move below methods to Importer or Nx Commons Lang?

    public static String trimAroundSubString(String wholeStr, String subStr) {
        if (wholeStr == null || subStr == null) {
            return wholeStr;
        }
        return wholeStr.replaceAll(
                "\\s*(" + Pattern.quote(subStr) + ")\\s*", "$1");
    }

    public static String trimBeforeSubString(String wholeStr, String subStr) {
        if (wholeStr == null || subStr == null) {
            return wholeStr;
        }
        return wholeStr.replaceAll("\\s*(" + Pattern.quote(subStr) + ")", "$1");
    }

    public static String trimAfterSubString(String wholeStr, String subStr) {
        if (wholeStr == null || subStr == null) {
            return wholeStr;
        }
        return wholeStr.replaceAll("(" + Pattern.quote(subStr) + ")\\s*", "$1");
    }

    /**
     * Parses a string containing one or more HTML/XML attributes
     * (e.g., key="value") and returns them all. This method deals with
     * attributes only but can also be passed actual HTML/XML mark-up.
     * Given the latter, this method will only consider attributes of the
     * first element encountered.
     * No attempt is made to first create a DOM model, so the string argument
     * does not have to be fully "valid" XML/HTML.
     * If the attribute string is <code>null</code> or produces no match,
     * an empty {@link Properties} is returned.
     * @param attribsStr the string containing attributes
     * @return attributes (never <code>null</code>)
     */
    public static Properties parseDomAttributes(String attribsStr) {
        return parseDomAttributes(attribsStr, false);
    }

    /**
     * Parses a string containing one or more HTML/XML attributes
     * (e.g., key="value") and returns them all. This method deals with
     * attributes only but can also be passed actual HTML/XML mark-up.
     * Given the latter, this method will only consider attributes of the
     * first element encountered.
     * No attempt is made to first create a DOM model, so the string argument
     * does not have to be fully "valid" XML/HTML.
     * If the attribute string is <code>null</code> or produces no match,
     * an empty {@link Properties} is returned.
     * @param attribsStr the string containing attributes
     * @param caseInsensitive whether the return properties
     *     has case-insensitive keys
     * @return attributes (never <code>null</code>)
     */
    public static Properties parseDomAttributes(
            String attribsStr, boolean caseInsensitive) {
        var props = new Properties(caseInsensitive);
        if (StringUtils.isBlank(attribsStr)) {
            return props;
        }
        doParseDomAttributes(attribsStr
                // strip before and after angle brackets as separate steps,
                // in case of weird mark-up
                .replaceFirst("(?s)^.*<\\s*[\\w-]+\\s*(.*)$", "$1")
                .replaceFirst("(?s)^(.*?)>.*$", "$1")
                .replaceAll("\\s+", " ")
                .replace(" =", "=")
                .replace("= ", "="),
                props);
        return props;
    }

    private static void doParseDomAttributes(
            String attribsStr, Properties attribs) {
        var m = Pattern.compile("^([\\w-]+)=(.+)")
                .matcher(attribsStr.trim());
        if (m.find()) {
            var name = m.group(1);
            var theRest = m.group(2);
            var quote = theRest.charAt(0);
            m = Pattern.compile(
                    (quote != '"' && quote != '\'')
                            // no quotes
                            ? "^.*?=(.+?)(\\s|>|$)"
                            // with quotes
                            : "^.*?=%1$s(.*?)%1$s".formatted(quote))
                    .matcher(attribsStr);
            if (m.find()) {
                var value = m.group(1);
                attribs.add(name, value);
                doParseDomAttributes(substring(attribsStr, m.end()), attribs);
            }
        }
    }
}
