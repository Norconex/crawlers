/* Copyright 2016-2023 Norconex Inc.
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
package com.norconex.crawler.web.recrawl.impl;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.recrawl.RecrawlableResolver;
import com.norconex.crawler.web.sitemap.SitemapChangeFrequency;

import lombok.Data;
import lombok.NoArgsConstructor;
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
 * sitemap directives using the {@link #setSitemapSupport(SitemapSupport)}
 * method.
 * </p>
 *
 * <h3>Custom recrawl frequencies:</h3>
 * <p>
 * You can chose to have some of your crawled documents be re-crawled less
 * frequently than others by specifying custom minimum frequencies
 * ({@link #setMinFrequencies(Collection)}). Minimum frequencies are
 * processed in the order specified and must each have to following:
 * </p>
 * <ul>
 *   <li>applyTo: Either "reference" or "contentType"
 *       (defaults to "reference").</li>
 *   <li>pattern: A regular expression.</li>
 *   <li>value: one of "always", "hourly", "daily", "weekly", "monthly",
 *       "yearly", "never", or a numeric value in milliseconds.</li>
 * </ul>
 *
 * <p>
 * As of 2.7.0, XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * {@nx.xml.usage
 * <recrawlableResolver
 *     class="com.norconex.crawler.web.recrawl.impl.GenericRecrawlableResolver"
 *     sitemapSupport="[first|last|never]" >
 *
 *   <minFrequency applyTo="[reference|contentType]"
 *       value="([always|hourly|daily|weekly|monthly|yearly|never] or milliseconds)">
 *     <matcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (Matcher for the reference or content type.)
 *     </matcher>
 *   </minFrequency>
 *   (... repeat frequency tag as needed ...)
 * </recrawlableResolver>
 * }
 *
 * {@nx.xml.example
 * <recrawlableResolver
 *     class="com.norconex.crawler.web.recrawl.impl.GenericRecrawlableResolver"
 *     sitemapSupport="last" >
 *   <minFrequency applyTo="contentType" value="monthly">
 *     <matcher>application/pdf</matcher>
 *   </minFrequency>
 *   <minFrequency applyTo="reference" value="1800000">
 *     <matcher method="regex">.*latest-news.*\.html</matcher>
 *   </minFrequency>
 * </recrawlableResolver>
 * }
 * <p>
 * The above example ensures PDFs are re-crawled no more frequently than
 * once a month, while HTML news can be re-crawled as fast at every half hour.
 * For the rest, it relies on the website sitemap directives (if any).
 * </p>
 *
 * @since 2.5.0
 */
@SuppressWarnings("javadoc")
@Slf4j
@Data
public class GenericRecrawlableResolver
        implements RecrawlableResolver, XMLConfigurable{

    public enum SitemapSupport {
        FIRST, LAST, NEVER;
        public static SitemapSupport getSitemapSupport(String sitemapSupport) {
            if (StringUtils.isBlank(sitemapSupport)) {
                return null;
            }
            for (SitemapSupport v : SitemapSupport.values()) {
                if (v.toString().equalsIgnoreCase(sitemapSupport)) {
                    return v;
                }
            }
            return null;
        }
    }

    private SitemapSupport sitemapSupport = SitemapSupport.FIRST;
    private final List<MinFrequency> minFrequencies = new ArrayList<>();

    /**
     * Gets the sitemap support strategy. Defualt is
     * {@link SitemapSupport#FIRST}.
     * @return sitemap support strategy
     */
    public SitemapSupport getSitemapSupport() {
        return sitemapSupport;
    }
    /**
     * Sets the sitemap support strategy. A <code>null</code> value
     * is equivalent to specifying the default {@link SitemapSupport#FIRST}.
     * @param sitemapSupport sitemap support strategy
     */
    public void setSitemapSupport(SitemapSupport sitemapSupport) {
        this.sitemapSupport = sitemapSupport;
    }

    /**
     * Gets minimum frequencies.
     * @return minimum frequencies
     */
    public List<MinFrequency> getMinFrequencies() {
        return Collections.unmodifiableList(minFrequencies);
    }
    /**
     * Sets minimum frequencies.
     * @param minFrequencies minimum frequencies
     * @since 3.0.0
     */
    public void setMinFrequencies(Collection<MinFrequency> minFrequencies) {
        CollectionUtil.setAll(this.minFrequencies, minFrequencies);
    }

    @Override
    public boolean isRecrawlable(WebDocRecord prevData) {

        // if never crawled: yes, crawl it
        if (prevData.getCrawlDate() == null) {
            return true;
        }

        var ss = sitemapSupport;
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

    private MinFrequency getMatchingMinFrequency(WebDocRecord prevData) {
        for (MinFrequency f : minFrequencies) {
            var applyTo = f.getApplyTo();
            if (StringUtils.isBlank(applyTo)) {
                applyTo = "reference";
            }
            if (("reference".equalsIgnoreCase(applyTo)
                    && f.getMatcher().matches(prevData.getReference())
                    || ("contentType".equalsIgnoreCase(applyTo)
                            && f.getMatcher().matches(
                                    prevData.getContentType().toString())))) {
                return f;
            }
        }
        return null;
    }

    private boolean hasSitemapFrequency(WebDocRecord prevData) {
        return StringUtils.isNotBlank(prevData.getSitemapChangeFreq());
    }
    private boolean hasSitemapLastModified(WebDocRecord prevData) {
        return prevData.getSitemapLastMod() != null;
    }

    private boolean isRecrawlableFromMinFrequencies(
            MinFrequency f, WebDocRecord prevData) {
        var value = f.getValue();
        if (StringUtils.isBlank(value)) {
            return true;
        }

        var cf =
                SitemapChangeFrequency.of(value);
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
                LOG.debug("""
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
            LOG.debug("""
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

    private boolean isRecrawlableFromSitemap(WebDocRecord prevData) {

        // If sitemap specifies a last modified date and it is more recent
        // than the the document last crawl date, recrawl it (otherwise don't).
        if (hasSitemapLastModified(prevData)) {
            var lastModified = prevData.getSitemapLastMod();
            var lastCrawled = prevData.getCrawlDate();
            LOG.debug("Sitemap last modified date is {} for: {}",
                    lastModified, prevData.getReference());
            if (lastModified.isAfter(lastCrawled)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Recrawlable according to sitemap directive "
                          + "(last modified '{}' > last crawled '{}'): {}",
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
            SitemapChangeFrequency cf, WebDocRecord prevData,
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

    @Data
    @NoArgsConstructor
    public static class MinFrequency {
        private String applyTo;
        private String value;
        private final TextMatcher matcher = new TextMatcher();
        public MinFrequency(String applyTo, String value, TextMatcher matcher) {
            this.applyTo = applyTo;
            this.value = value;
            this.matcher.copyFrom(matcher);
        }
        public void setMatcher(TextMatcher matcher) {
            this.matcher.copyFrom(matcher);
        }
    }

    @Override
    public void loadFromXML(XML xml) {
        var smsXml = xml.getString("@sitemapSupport");
        if (StringUtils.isNotBlank(smsXml)) {
            var sms = SitemapSupport.getSitemapSupport(smsXml);
            if (sms == null) {
                LOG.warn("Unsupported sitemap support value: \"{}\". "
                        + "Will use default.", smsXml);
            }
            setSitemapSupport(sms);
        }

        List<MinFrequency> frequencies = new ArrayList<>();
        for (XML minFreqXml : xml.getXMLList("minFrequency")) {
            var f = new MinFrequency();
            f.setApplyTo(minFreqXml.getString("@applyTo"));
            f.setValue(minFreqXml.getString("@value"));
            f.matcher.loadFromXML(minFreqXml.getXML("matcher"));
            frequencies.add(f);
        }
        setMinFrequencies(frequencies);
    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("sitemapSupport", sitemapSupport);
        for (MinFrequency mf : minFrequencies) {
            var minFreqXml = xml.addElement("minFrequency")
                    .setAttribute("applyTo", mf.applyTo)
                    .setAttribute("value", mf.value);
            mf.matcher.saveToXML(minFreqXml.addElement("matcher"));
        }
    }
}
