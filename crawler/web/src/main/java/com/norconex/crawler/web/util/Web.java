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

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.WebCrawlerContext;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.operations.scope.UrlScope;
import com.norconex.crawler.web.event.WebCrawlerEvent;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.robot.RobotsTxt;

import lombok.NonNull;

public final class Web {

    private Web() {}

    public static void fireIfUrlOutOfScope(
            Crawler crawler,
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


//    private static final BeanMapper BEAN_MAPPER =
//            CrawlSessionBeanMapperFactory.create(
//                    WebCrawlerConfig.class, b ->
//                        b.unboundPropertyMapping(
//                                "crawler", WebCrawlerMixIn.class));
//    private static class WebCrawlerMixIn {
//        @JsonDeserialize(as = WebCrawlerConfig.class)
//        private CrawlerConfig configuration;
//    }

//    public static BeanMapper beanMapper() {
//        return BEAN_MAPPER;
//    }

//    public static WebCrawlerConfig config(CrawlerConfig cfg) {
//        return (WebCrawlerConfig) cfg;
//    }
//    public static WebCrawlerConfig config(AbstractPipelineContext ctx) {
//        return (WebCrawlerConfig) Web.config(ctx.getCrawler());
//    }
    public static WebCrawlerConfig config(Crawler crawler) {
        return (WebCrawlerConfig) crawler.getConfiguration();
    }

    public static WebCrawlerContext crawlerContext(Crawler crawler) {
        return (WebCrawlerContext) crawler.getContext();
    }

//    public static WebImporterPipelineContext importerContext(
//            AbstractPipelineContext ctx) {
//        return (WebImporterPipelineContext) ctx;
//    }

//    //TODO move this one to core?
//    public static void fire(
//            Crawler crawler,
//            @NonNull
//            Consumer<CrawlerEventBuilder<?, ?>> c) {
//        if (crawler != null) {
//            var builder = CrawlerEvent.builder();
//            c.accept(builder);
//            crawler.getEventManager().fire(builder.build());
//        }
//    }

    //TODO could probably move this where needed since generically,
    // we would get the fetcher wrapper directly from crawler.
    public static List<HttpFetcher> toHttpFetcher(
            @NonNull Collection<Fetcher<?, ?>> fetchers) {
        return fetchers.stream()
            .map(HttpFetcher.class::cast)
            .toList();
    }

    public static HttpFetcher fetcher(Crawler crawler) {
        return (HttpFetcher) crawler.getFetcher();
    }

    public static WebCrawlDocContext docContext(@NonNull CrawlDoc crawlDoc) {
        return (WebCrawlDocContext) crawlDoc.getDocContext();
    }
    public static WebCrawlDocContext cachedDocContext(
            @NonNull CrawlDoc crawlDoc) {
        return (WebCrawlDocContext) crawlDoc.getCachedDocContext();
    }

    public static RobotsTxt robotsTxt(Crawler crawler, String reference) {
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
        doParseDomAttributes(
                attribsStr
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
            m = Pattern.compile((quote != '"' && quote != '\'')
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
