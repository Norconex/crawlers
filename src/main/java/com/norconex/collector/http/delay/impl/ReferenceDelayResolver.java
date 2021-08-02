/* Copyright 2016-2018 Norconex Inc.
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
package com.norconex.collector.http.delay.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Introduces different delays between document downloads based on matching
 * document reference (URL) patterns.
 * There are a few ways the actual delay value can be defined (in order):
 * </p>
 * <ol>
 *   <li>Takes the delay specify by a robots.txt file.
 *       Only applicable if robots.txt files and its robots crawl delays
 *       are not ignored.</li>
 *   <li>Takes the delay matching a reference pattern, if any (picks the first
 *       one matching).</li>
 *   <li>Used the specified default delay or 3 seconds, if none is
 *       specified.</li>
 * </ol>
 * <p>
 * One of these following scope dictates how the delay is applied, listed
 * in order from the best behaved to the least.
 * </p>
 * <ul>
 *   <li><b>crawler</b>: the delay is applied between each URL download
 *       within a crawler instance, regardless how many threads are defined
 *       within that crawler, or whether URLs are from the
 *       same site or not.  This is the default scope.</li>
 *   <li><b>site</b>: the delay is applied between each URL download
 *       from the same site within a crawler instance, regardless how many
 *       threads are defined. A site is defined by a URL protocol and its
 *       domain (e.g. http://example.com).</li>
 *   <li><b>thread</b>: the delay is applied between each URL download from
 *       any given thread.  The more threads you have the less of an
 *       impact the delay will have.</li>
 * </ul>
 *
 * <p>
 * As of 2.7.0, XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * {@nx.xml.usage
 * <delay class="com.norconex.collector.http.delay.impl.ReferenceDelayResolver"
 *         default="(milliseconds)"
 *         ignoreRobotsCrawlDelay="[false|true]"
 *         scope="[crawler|site|thread]">
 *     <pattern delay="(delay in milliseconds)">
 *       (regular expression applied against document reference)
 *     </pattern>
 *
 *     (... repeat pattern tag as needed ...)
 * </delay>
 * }
 *
 * {@nx.xml.example
 * <pre>
 * <delay class="ReferenceDelayResolver" default="3 seconds" >
 *     <pattern delay="10 seconds">.*\.pdf</pattern>
 * </delay>
 * }
 * <p>
 * The above examlpe will increase the delay to 10 seconds when encountering
 * PDFs from a default of 3 seconds.
 * </p>
 *
 * @author Pascal Essiembre
 * @since 2.5.0
 */
public class ReferenceDelayResolver extends AbstractDelayResolver {

    private List<DelayReferencePattern> delayPatterns = new ArrayList<>();

    public ReferenceDelayResolver() {
        super();
    }

    public List<DelayReferencePattern> getDelayReferencePatterns() {
        return delayPatterns;
    }
    public void setDelayReferencePatterns(
            List<DelayReferencePattern> delayPatterns) {
        this.delayPatterns = delayPatterns;
    }

    @Override
    protected long resolveExplicitDelay(String url) {
        long delay = -1;
        for (DelayReferencePattern delayPattern : delayPatterns) {
            if (delayPattern.matches(url)) {
                delay = delayPattern.getDelay();
                break;
            }
        }
        return delay;
    }

    @Override
    protected void loadDelaysFromXML(XML xml) {
        for (XML pxml : xml.getXMLList("pattern")) {
            delayPatterns.add(new DelayReferencePattern(
                    pxml.getString(".", ""),
                    pxml.getDurationMillis("@delay", DEFAULT_DELAY)));
        }
    }

    @Override
    protected void saveDelaysToXML(XML xml) {
        for (DelayReferencePattern delayPattern : delayPatterns) {
            xml.addElement("pattern", delayPattern.getPattern())
                    .setAttribute("delay", delayPattern.getDelay());
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
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }

    public static class DelayReferencePattern {
        private final String pattern;
        private final long delay;

        public DelayReferencePattern(String pattern, long delay) {
            super();
            this.pattern = pattern;
            this.delay = delay;
        }
        public boolean matches(String reference) {
            return reference.matches(pattern);
        }
        public long getDelay() {
            return delay;
        }
        public String getPattern() {
            return pattern;
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
            return new ReflectionToStringBuilder(
                    this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
        }
    }
}

