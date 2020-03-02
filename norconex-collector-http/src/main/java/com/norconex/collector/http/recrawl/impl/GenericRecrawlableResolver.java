/* Copyright 2016-2020 Norconex Inc.
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
package com.norconex.collector.http.recrawl.impl;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.recrawl.IRecrawlableResolver;
import com.norconex.collector.http.sitemap.SitemapChangeFrequency;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

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
 * ({@link #setMinFrequencies(MinFrequency...)}). Minimum frequencies are
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
 *     class="com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver"
 *     sitemapSupport="[first|last|never]" >
 *
 *   <minFrequency applyTo="[reference|contentType]" caseSensitive="[false|true]"
 *       value="([always|hourly|daily|weekly|monthly|yearly|never] or milliseconds)">
 *     (regex pattern)
 *   </minFrequency>
 *   (... repeat frequency tag as needed ...)
 * </recrawlableResolver>
 * }
 *
 * {@nx.xml.example
 * <recrawlableResolver
 *     class="com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver"
 *     sitemapSupport="last" >
 *   <minFrequency applyTo="contentType" value="monthly">application/pdf</minFrequency>
 *   <minFrequency applyTo="reference" value="1800000">.*latest-news.*\.html</minFrequency>
 * </recrawlableResolver>
 * }
 * <p>
 * The above example ensures PDFs are re-crawled no more frequently than
 * once a month, while HTML news can be re-crawled as fast at every half hour.
 * For the rest, it relies on the website sitemap directives (if any).
 * </p>
 *
 * @author Pascal Essiembre
 * @since 2.5.0
 */
public class GenericRecrawlableResolver
        implements IRecrawlableResolver, IXMLConfigurable{

    private static final Logger LOG =
            LoggerFactory.getLogger(GenericRecrawlableResolver.class);

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
     */
    public void setMinFrequencies(MinFrequency... minFrequencies) {
        CollectionUtil.setAll(this.minFrequencies, minFrequencies);
    }
    /**
     * Sets minimum frequencies.
     * @param minFrequencies minimum frequencies
     * @since 3.0.0
     */
    public void setMinFrequencies(List<MinFrequency> minFrequencies) {
        CollectionUtil.setAll(this.minFrequencies, minFrequencies);
    }

    @Override
    public boolean isRecrawlable(HttpDocInfo prevData) {

        // if never crawled: yes, crawl it
        if (prevData.getCrawlDate() == null) {
            return true;
        }

        SitemapSupport ss = sitemapSupport;
        if (ss == null) {
            ss = SitemapSupport.FIRST;
        }
        boolean hasSitemapInstructions =
                hasSitemapFrequency(prevData)
                        || hasSitemapLastModified(prevData);

        if (ss == SitemapSupport.FIRST && hasSitemapInstructions) {
            return isRecrawlableFromSitemap(prevData);
        }

        MinFrequency f = getMatchingMinFrequency(prevData);
        if (f != null) {
            return isRecrawlableFromMinFrequencies(f, prevData);
        }

        if (ss == SitemapSupport.LAST && hasSitemapInstructions) {
            return isRecrawlableFromSitemap(prevData);
        }

        // if we have not found a reason not to recrawl, then recrawl
        return true;
    }

    private MinFrequency getMatchingMinFrequency(HttpDocInfo prevData) {
        for (MinFrequency f : minFrequencies) {
            if (f.pattern == null || f.value == null) {
                LOG.warn("Value or pattern missing in minimum frequency.");
                continue;
            }
            String applyTo = f.getApplyTo();
            if (StringUtils.isBlank(applyTo)) {
                applyTo = "reference";
            }
            if ("reference".equalsIgnoreCase(applyTo)
                    && f.getCachedPattern().matcher(
                            prevData.getReference()).matches()) {
                return f;
            }
            if ("contentType".equalsIgnoreCase(applyTo)
                    && f.getCachedPattern().matcher(
                            prevData.getContentType().toString()).matches()) {
                return f;
            }
        }
        return null;
    }


    private boolean hasSitemapFrequency(HttpDocInfo prevData) {
        return StringUtils.isNotBlank(prevData.getSitemapChangeFreq());
    }
    private boolean hasSitemapLastModified(HttpDocInfo prevData) {
        return prevData.getSitemapLastMod() != null;
//                && prevData.getSitemapLastMod() > 0;
    }

    private boolean isRecrawlableFromMinFrequencies(
            MinFrequency f, HttpDocInfo prevData) {
        String value = f.getValue();
        if (StringUtils.isBlank(value)) {
            return true;
        }

        SitemapChangeFrequency cf =
                SitemapChangeFrequency.getChangeFrequency(value);
        if (cf != null) {
            return isRecrawlableFromFrequency(cf, prevData, "custom");
        }

        long millis;
        if (NumberUtils.isDigits(value)) {
            millis = NumberUtils.toLong(value);
        } else {
            millis = new DurationParser().parse(value).toMillis();
        }
        ZonedDateTime lastCrawlDate = prevData.getCrawlDate();
        ZonedDateTime minCrawlDate = lastCrawlDate.plus(
                millis, ChronoField.MILLI_OF_DAY.getBaseUnit());
        ZonedDateTime now = ZonedDateTime.now();
        if (minCrawlDate.isBefore(now)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Recrawlable according to custom directive "
                      + "(required elasped time '{}' "
                      + "< actual elasped time '{}' since '{}'): {}",
                      formatDuration(millis),
                      formatDuration(lastCrawlDate, now),
                      lastCrawlDate,
                      prevData.getReference());
            }
            return true;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Not recrawlable according to custom directive "
                  + "(required elasped time '{}' "
                  + ">= actual elasped time '{}' "
                  + "since '{}'): {}",
                  formatDuration(millis),
                  formatDuration(lastCrawlDate, now),
                  lastCrawlDate,
                  prevData.getReference());
        }
        return false;
    }

    private boolean isRecrawlableFromSitemap(HttpDocInfo prevData) {

        // If sitemap specifies a last modified date and it is more recent
        // than the the document last crawl date, recrawl it (otherwise don't).
        if (hasSitemapLastModified(prevData)) {
            ZonedDateTime lastModified = prevData.getSitemapLastMod();
            ZonedDateTime lastCrawled = prevData.getCrawlDate();
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
        SitemapChangeFrequency cf = SitemapChangeFrequency.getChangeFrequency(
                prevData.getSitemapChangeFreq());

        return isRecrawlableFromFrequency(cf, prevData, "Sitemap");
    }


    private boolean isRecrawlableFromFrequency(
            SitemapChangeFrequency cf, HttpDocInfo prevData,
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

        ZonedDateTime lastCrawlDate = prevData.getCrawlDate();
        ZonedDateTime minCrawlDate = prevData.getCrawlDate();
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

        ZonedDateTime now = ZonedDateTime.now();
        if (minCrawlDate.isBefore(now)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format(
                        "Recrawlable according to %s directive "
                      + "(required elasped time '%s' "
                      + "< actual elasped time '%s' since '%s'): %s",
                      context,
                      formatDuration(lastCrawlDate, minCrawlDate),
                      formatDuration(lastCrawlDate, now),
                      lastCrawlDate,
                      prevData.getReference()));
            }
            return true;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format(
                    "Not recrawlable according to %s directive "
                  + "(required elasped time '%s' "
                  + ">= actual elasped time '%s' since '%s'): %s",
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

    public static class MinFrequency {
        private String applyTo;
        private String value;
        private String pattern;
        private Pattern cachedPattern;
        private boolean caseSensitive;
        public MinFrequency() {
            super();
        }
        public MinFrequency(String applyTo, String value, String pattern) {
            super();
            this.applyTo = applyTo;
            this.value = value;
            setPattern(pattern);
        }
        public String getApplyTo() {
            return applyTo;
        }
        public void setApplyTo(String applyTo) {
            this.applyTo = applyTo;
        }
        public String getValue() {
            return value;
        }
        public void setValue(String value) {
            this.value = value;
        }
        public String getPattern() {
            return pattern;
        }
        public void setPattern(String pattern) {
            this.pattern = pattern;
            cachedPattern = null;
        }
        public boolean isCaseSensitive() {
            return caseSensitive;
        }
        public void setCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
            cachedPattern = null;
        }

        private synchronized Pattern getCachedPattern() {
            if (cachedPattern != null) {
                return cachedPattern;
            }
            Pattern p;
            if (pattern == null) {
                p = null;
            } else {
                int flags = Pattern.DOTALL;
                if (!caseSensitive) {
                    flags = flags | Pattern.CASE_INSENSITIVE
                            | Pattern.UNICODE_CASE;
                }
                p = Pattern.compile(pattern, flags);
            }
            cachedPattern = p;
            return p;
        }

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .append("applyTo", applyTo)
                    .append("value", value)
                    .append("pattern", pattern)
                    .append("caseSensitive", caseSensitive)
                    .toString();
        }
        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof MinFrequency)) {
                return false;
            }
            MinFrequency castOther = (MinFrequency) other;
            return new EqualsBuilder()
                    .append(applyTo, castOther.applyTo)
                    .append(value, castOther.value)
                    .append(pattern, castOther.pattern)
                    .append(caseSensitive, castOther.caseSensitive)
                    .isEquals();
        }
        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                        .append(applyTo)
                        .append(value)
                        .append(pattern)
                        .append(caseSensitive)
                        .toHashCode();
        }
    }

    @Override
    public void loadFromXML(XML xml) {
        String smsXml = xml.getString("@sitemapSupport");
        if (StringUtils.isNotBlank(smsXml)) {
            SitemapSupport sms = SitemapSupport.getSitemapSupport(smsXml);
            if (sms == null) {
                LOG.warn("Unsupported sitemap support value: \"{}\". "
                        + "Will use default.", smsXml);
            }
            setSitemapSupport(sms);
        }

        List<MinFrequency> frequencies = new ArrayList<>();
        for (XML x : xml.getXMLList("minFrequency")) {
            MinFrequency f = new MinFrequency();
            f.setApplyTo(x.getString("@applyTo"));
            f.setCaseSensitive(x.getBoolean("@caseSensitive", false));
            f.setValue(x.getString("@value"));
            f.setPattern(x.getString("."));
            frequencies.add(f);
        }

        setMinFrequencies(frequencies);
    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("sitemapSupport", sitemapSupport);
        for (MinFrequency mf : minFrequencies) {
            xml.addElement("minFrequency", mf.pattern)
                    .setAttribute("applyTo", mf.applyTo)
                    .setAttribute("value", mf.value)
                    .setAttribute("caseSensitive", mf.caseSensitive);
        }
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this,
                ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
