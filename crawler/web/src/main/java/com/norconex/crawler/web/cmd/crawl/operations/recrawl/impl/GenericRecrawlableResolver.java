/* Copyright 2016-2024 Norconex Inc.
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
package com.norconex.crawler.web.cmd.crawl.operations.recrawl.impl;

import static java.util.Optional.ofNullable;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.Collection;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.crawler.web.cmd.crawl.operations.recrawl.RecrawlableResolver;
import com.norconex.crawler.web.cmd.crawl.operations.recrawl.impl.GenericRecrawlableResolverConfig.MinFrequency;
import com.norconex.crawler.web.cmd.crawl.operations.recrawl.impl.GenericRecrawlableResolverConfig.SitemapSupport;
import com.norconex.crawler.web.cmd.crawl.operations.recrawl.impl.GenericRecrawlableResolverConfig.MinFrequency.ApplyTo;
import com.norconex.crawler.web.cmd.crawl.operations.sitemap.SitemapChangeFrequency;
import com.norconex.crawler.web.doc.WebCrawlDocContext;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Relies on both sitemap directives and custom instructions for
 * establishing the minimum frequency between each document recrawl.
 * </p>
 *
 * <h3>Sitemap support:</h3>
 * <p>
 * Provided crawler support for sitemaps has not been disabled,
 * this class tries to honor last modified and frequency directives found
 * in sitemap files.
 * </p>
 * <p>
 * By default, existing sitemap directives take precedence over custom ones.
 * You chose to have sitemap directives be considered last or even disable
 * sitemap directives using the
 * {@link GenericRecrawlableResolverConfig#setSitemapSupport(SitemapSupport)}
 * method.
 * </p>
 *
 * <h3>Custom recrawl frequencies:</h3>
 * <p>
 * You can chose to have some of your crawled documents be re-crawled less
 * frequently than others by specifying custom minimum frequencies with
 * ({@link GenericRecrawlableResolverConfig#setMinFrequencies(Collection)}).
 * Minimum frequencies are processed in the order specified.
 * </p>
 *
 * @since 2.5.0
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class GenericRecrawlableResolver implements
        RecrawlableResolver, Configurable<GenericRecrawlableResolverConfig> {

    @Getter
    private final GenericRecrawlableResolverConfig configuration =
            new GenericRecrawlableResolverConfig();

    @Override
    public boolean isRecrawlable(WebCrawlDocContext prevData) {

        // if never crawled: yes, crawl it
        if (prevData.getCrawlDate() == null) {
            return true;
        }

        var ss = configuration.getSitemapSupport();
        if (ss == null) {
            ss = SitemapSupport.FIRST;
        }
        var hasSitemapInstructions =
                hasSitemapFrequency(prevData)
                        || hasSitemapLastModified(prevData);

        if (ss == SitemapSupport.FIRST && hasSitemapInstructions) {
            return isRecrawlableFromSitemap(prevData);
        }

        var f = getMatchingMinFrequency(prevData);
        if (f != null) {
            return isRecrawlableFromMinFrequencies(f, prevData);
        }

        if (ss == SitemapSupport.LAST && hasSitemapInstructions) {
            return isRecrawlableFromSitemap(prevData);
        }

        // if we have not found a reason not to recrawl, then recrawl
        return true;
    }

    private MinFrequency getMatchingMinFrequency(WebCrawlDocContext prevData) {
        for (MinFrequency f : configuration.getMinFrequencies()) {
            var applyTo = ofNullable(f.getApplyTo()).orElse(ApplyTo.REFERENCE);
            var matchMe = applyTo == ApplyTo.REFERENCE
                    ? prevData.getReference()
                    : prevData.getContentType().toString();
            if (f.getMatcher().matches(matchMe)) {
                return f;
            }
        }
        return null;
    }

    private boolean hasSitemapFrequency(WebCrawlDocContext prevData) {
        return StringUtils.isNotBlank(prevData.getSitemapChangeFreq());
    }

    private boolean hasSitemapLastModified(WebCrawlDocContext prevData) {
        return prevData.getSitemapLastMod() != null;
    }

    private boolean isRecrawlableFromMinFrequencies(
            MinFrequency f, WebCrawlDocContext prevData) {
        var value = f.getValue();
        if (StringUtils.isBlank(value)) {
            return true;
        }

        var cf = SitemapChangeFrequency.of(value);
        if (cf != null) {
            return isRecrawlableFromFrequency(cf, prevData, "custom");
        }

        long millis;
        if (NumberUtils.isDigits(value)) {
            millis = NumberUtils.toLong(value);
        } else {
            millis = new DurationParser().parse(value).toMillis();
        }
        var lastCrawlDate = prevData.getCrawlDate();
        var minCrawlDate = lastCrawlDate.plus(
                millis, ChronoField.MILLI_OF_DAY.getBaseUnit());
        var now = ZonedDateTime.now();
        if (minCrawlDate.isBefore(now)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        """
                                Recrawlable according to custom directive\s\
                                (required elasped time '{}'\s\
                                < actual elasped time '{}' since '{}'): {}""",
                        formatDuration(millis),
                        formatDuration(lastCrawlDate, now),
                        lastCrawlDate,
                        prevData.getReference());
            }
            return true;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(
                    """
                            Not recrawlable according to custom directive\s\
                            (required elasped time '{}'\s\
                            >= actual elasped time '{}'\s\
                            since '{}'): {}""",
                    formatDuration(millis),
                    formatDuration(lastCrawlDate, now),
                    lastCrawlDate,
                    prevData.getReference());
        }
        return false;
    }

    private boolean isRecrawlableFromSitemap(WebCrawlDocContext prevData) {

        // If sitemap specifies a last modified date and it is more recent
        // than the the document last crawl date, recrawl it (otherwise don't).
        if (hasSitemapLastModified(prevData)) {
            var lastModified = prevData.getSitemapLastMod();
            var lastCrawled = prevData.getCrawlDate();
            LOG.debug(
                    "Sitemap last modified date is {} for: {}",
                    lastModified, prevData.getReference());
            if (lastModified.isAfter(lastCrawled)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("""
                        Recrawlable according to sitemap directive \
                        (last modified '{}' > last crawled '{}'): {}""",
                            lastModified, lastCrawled, prevData.getReference());
                }
                return true;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Not recrawlable according to sitemap directive "
                        + "(last modified '{}' > last crawled '{}'): {}",
                        lastModified, lastCrawled, prevData.getReference());
            }
            return false;
        }

        // If sitemap specifies a change frequency, check if we are past
        // it and recrawl if so (otherwise don't).
        var cf = SitemapChangeFrequency.of(
                prevData.getSitemapChangeFreq());

        return isRecrawlableFromFrequency(cf, prevData, "Sitemap");
    }

    private boolean isRecrawlableFromFrequency(
            SitemapChangeFrequency cf, WebCrawlDocContext prevData,
            String context) {
        if (cf == null) {
            return true;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("The {} change frequency is {} for: {}",
                    context, cf, prevData.getReference());
        }
        if (cf == SitemapChangeFrequency.ALWAYS) {
            return true;
        }
        if (cf == SitemapChangeFrequency.NEVER) {
            return false;
        }

        var lastCrawlDate = prevData.getCrawlDate();
        var minCrawlDate = prevData.getCrawlDate();
        switch (cf) {
            case HOURLY:
                minCrawlDate = minCrawlDate.plusHours(1);
                break;
            case DAILY:
                minCrawlDate = minCrawlDate.plusDays(1);
                break;
            case WEEKLY:
                minCrawlDate = minCrawlDate.plusWeeks(1);
                break;
            case MONTHLY:
                minCrawlDate = minCrawlDate.plusMonths(1);
                break;
            case YEARLY:
                minCrawlDate = minCrawlDate.plusYears(1);
                break;
            default:
                break;
        }

        var now = ZonedDateTime.now();
        if (minCrawlDate.isBefore(now)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("""
                        Recrawlable according to {} directive\s\
                        (required elasped time '{}'\s\
                        < actual elasped time '{}' since '{}'): {}""",
                        context,
                        formatDuration(lastCrawlDate, minCrawlDate),
                        formatDuration(lastCrawlDate, now),
                        lastCrawlDate,
                        prevData.getReference());
            }
            return true;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("""
                    Not recrawlable according to {} directive\s\
                    (required elapsed time '{}'\s\
                    >= actual elapsed time '{}' since '{}'): {}""",
                    context,
                    formatDuration(lastCrawlDate, minCrawlDate),
                    formatDuration(lastCrawlDate, now),
                    lastCrawlDate,
                    prevData.getReference()));
        }
        return false;
    }

    private String formatDuration(ZonedDateTime start, ZonedDateTime end) {
        return formatDuration(Duration.between(start, end));
    }

    private String formatDuration(Duration duration) {
        return formatDuration(duration.toMillis());
    }

    private String formatDuration(long millis) {
        return new DurationFormatter()
                .withLocale(Locale.ENGLISH)
                .format(millis);
    }
}
