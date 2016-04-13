/* Copyright 2016 Norconex Inc.
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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;

import com.norconex.collector.http.recrawl.IRecrawlableResolver;
import com.norconex.collector.http.recrawl.PreviousCrawlData;
import com.norconex.collector.http.sitemap.SitemapChangeFrequency;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

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
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;recrawlableResolver 
 *         class="com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver"
 *         sitemapSupport="[first|last|never]" &gt;
 *     
 *     &lt;minFrequency applyTo="[reference|contentType]" caseSensitive="[false|true]"
 *             value="([always|hourly|daily|weekly|monthly|yearly|never] or milliseconds)" &gt;
 *         (regex pattern)
 *     &lt;/minFrequency&gt;
 *     (... repeat frequency tag as needed ...)
 *     
 *  &lt;/recrawlableResolver&gt;
 * </pre>
 * 
 * <b>Example:</b>
 * <p>
 * The following example ensures PDFs recrawled no more frequently than 
 * once a month, while HTML news can be crawled as fast at every half hour.
 * For the rest, it relies on the website sitemap directives (if any).
 * </p>
 * <pre>
 *  &lt;recrawlableResolver 
 *         class="com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver"
 *         sitemapSupport="last" &gt;
 *     &lt;minFrequency applyTo="contentType" value="monthly"&gt;application/pdf&lt;/minFrequency&gt;
 *     &lt;minFrequency applyTo="reference" value="1090"&gt;.*latest-news.*\.html&lt;/minFrequency&gt;
 *  &lt;/recrawlableResolver&gt;
 * </pre>
 * 
 * @author Pascal Essiembre
 * @since 2.5.0
 */
public class GenericRecrawlableResolver 
        implements IRecrawlableResolver, IXMLConfigurable{

    private static final Logger LOG = 
            LogManager.getLogger(GenericRecrawlableResolver.class);
    
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

    
    public MinFrequency[] getMinFrequencies() {
        return minFrequencies.toArray(new MinFrequency[]{});
    }
    public void setMinFrequencies(MinFrequency... frequencies) {
        this.minFrequencies.clear();
        this.minFrequencies.addAll(Arrays.asList(frequencies));
    }
    
    @Override
    public boolean isRecrawlable(PreviousCrawlData prevData) {

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

    
    private MinFrequency getMatchingMinFrequency(PreviousCrawlData prevData) {
        for (MinFrequency f : minFrequencies) {
            if (f.regex == null || f.value == null) {
                LOG.warn("Value or pattern missing in minimum frequency.");
                continue;
            }
            String applyTo = f.getApplyTo();
            if (StringUtils.isBlank(applyTo)) {
                applyTo = "reference";
            }
            if ("reference".equalsIgnoreCase(applyTo)
                    && f.regex.matcher(prevData.getReference()).matches()) {
                return f;
            }
            if ("contentType".equalsIgnoreCase(applyTo) && f.regex.matcher(
                    prevData.getContentType().toString()).matches()) {
                return f;
            }
        }
        return null;
    }
    
    
    private boolean hasSitemapFrequency(PreviousCrawlData prevData) {
        return StringUtils.isNotBlank(prevData.getSitemapChangeFreq());
    }
    private boolean hasSitemapLastModified(PreviousCrawlData prevData) {
        return prevData.getSitemapLastMod() != null
                && prevData.getSitemapLastMod() > 0;
    }
    
    private boolean isRecrawlableFromMinFrequencies(
            MinFrequency f, PreviousCrawlData prevData) {
        String value = f.getValue();
        if (StringUtils.isBlank(value)) {
            return true;
        }
        
        if (NumberUtils.isDigits(value)) {
            DateTime minCrawlDate = new DateTime(prevData.getCrawlDate());
            int millis = NumberUtils.toInt(value);
            minCrawlDate = minCrawlDate.plusMillis(millis);
            if (minCrawlDate.isBeforeNow()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Recrawl suggested according to custom "
                            + "directive (min frequency < elapsed "
                            + "time since "
                            + prevData.getCrawlDate() + ") for: "
                            + prevData.getReference());
                }
                return true;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("No recrawl suggested according to custom "
                        + "directive (min frequency >= elapsed time since "
                        + prevData.getCrawlDate() + ") for: "
                        + prevData.getReference());
            }
            return false;
        }
        
        SitemapChangeFrequency cf = 
                SitemapChangeFrequency.getChangeFrequency(f.getValue());
        return isRecrawlableFromFrequency(cf, prevData, "custom");
    }
    
    private boolean isRecrawlableFromSitemap(PreviousCrawlData prevData) {
        
        // If sitemap specifies a last modified date and it is more recent
        // than the the document last crawl date, recrawl it (otherwise don't).
        if (hasSitemapLastModified(prevData)) {
            DateTime lastModified = new DateTime(prevData.getSitemapLastMod());
            LOG.debug("Sitemap last modified date is "
                    + lastModified + " for: " + prevData.getReference());
            if (lastModified.isAfter(prevData.getCrawlDate().getTime())) {
                LOG.debug("Recrawl suggested according to sitemap directive "
                        + "(last modified > last crawl date) for: "
                        + prevData.getReference());
                return true;
            }
            LOG.debug("No recrawl suggested according to sitemap directive "
                    + "(last modified <= last crawl date) for: "
                    + prevData.getReference());
            return false;
        }        
        
        // If sitemap specifies a change frequency, check if we are past
        // it and recrawl if so (otherwise don't).
        SitemapChangeFrequency cf = SitemapChangeFrequency.getChangeFrequency(
                prevData.getSitemapChangeFreq());
        
        return isRecrawlableFromFrequency(cf, prevData, "Sitemap");
    }
    
    
    private boolean isRecrawlableFromFrequency(
            SitemapChangeFrequency cf, PreviousCrawlData prevData,
            String context) {
        if (cf == null) {
            return true;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("The " + context + " change frequency is "
                    + cf + " for: " + prevData.getReference());
        }
        if (cf == SitemapChangeFrequency.ALWAYS) {
            return true;
        }
        if (cf == SitemapChangeFrequency.NEVER) {
            return false;
        }

        DateTime minCrawlDate = new DateTime(prevData.getCrawlDate());
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
        
        if (minCrawlDate.isBeforeNow()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Recrawl suggested according to " + context
                        + " directive (change frequency < elapsed time since "
                        + prevData.getCrawlDate() + ") for: "
                        + prevData.getReference());
            }
            return true;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("No recrawl suggested according to " + context
                    + " directive (change frequency >= elapsed time since "
                    + prevData.getCrawlDate() + ") for: "
                    + prevData.getReference());
        }
        return false;
    }
    
    public static class MinFrequency {
        private String applyTo;
        private String value;
        private String pattern;
        private Pattern regex;
        private boolean caseSensitive;
        public MinFrequency() {
            super();
        }
        public MinFrequency(String applyTo, String value, String pattern) {
            super();
            this.applyTo = applyTo;
            this.value = value;
            this.pattern = pattern;
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
            if (pattern == null) {
                regex = null;
            } else {
                int flags = Pattern.DOTALL;
                if (!caseSensitive) {
                    flags = flags | Pattern.CASE_INSENSITIVE 
                            | Pattern.UNICODE_CASE;
                }
                regex = Pattern.compile(pattern, flags);
            }
        }
        public boolean isCaseSensitive() {
            return caseSensitive;
        }
        public void setCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
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
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
        
        String smsXml = xml.getString("[@sitemapSupport]");
        if (StringUtils.isNotBlank(smsXml)) {
            SitemapSupport sms = SitemapSupport.getSitemapSupport(smsXml);
            if (sms == null) {
                LOG.warn("Unsupported sitemap support value: \"" + smsXml
                        + "\". Will use default.");
            }
            setSitemapSupport(sms);
        }
        
        List<HierarchicalConfiguration> nodes = 
                xml.configurationsAt("minFrequency");
        List<MinFrequency> frequencies = new ArrayList<>();
        for (HierarchicalConfiguration node : nodes) {
            MinFrequency f = new MinFrequency();
            f.setApplyTo(node.getString("[@applyTo]"));
            f.setCaseSensitive(node.getBoolean("[@caseSensitive]", false));
            f.setValue(node.getString("[@value]"));
            f.setPattern(node.getString(""));
            frequencies.add(f);
        }
        setMinFrequencies(frequencies.toArray(new MinFrequency[]{}));
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeStartElement("recrawlableResolver");
            writer.writeAttribute("class", getClass().getCanonicalName());
            
            if (getSitemapSupport() != null) {
                writer.writeAttribute(
                        "sitemapSupport", getSitemapSupport().toString());
            }
            
            for (MinFrequency mf : minFrequencies) {
                writer.writeStartElement("minFrequency");
                writer.writeAttributeString("applyTo", mf.getApplyTo());
                writer.writeAttributeString("value", mf.getValue());
                writer.writeAttributeBoolean(
                        "caseSensitive", mf.isCaseSensitive());
                writer.writeCharacters(mf.getPattern());
                writer.writeEndElement();
            }

            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
    
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("sitemapSupport", sitemapSupport)
                .append("minFrequencies", minFrequencies)
                .toString();
    }
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof GenericRecrawlableResolver)) {
            return false;
        }
        GenericRecrawlableResolver castOther = 
                (GenericRecrawlableResolver) other;
        return new EqualsBuilder()
                .append(sitemapSupport, castOther.sitemapSupport)
                .append(minFrequencies, castOther.minFrequencies)
                .isEquals();
    }
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                    .append(sitemapSupport)
                    .append(minFrequencies)
                    .toHashCode();
    }
}
