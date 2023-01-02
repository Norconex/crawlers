/* Copyright 2021-2022 Norconex Inc.
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
package com.norconex.crawler.core.crawler.event.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.IEventListener;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Alternative to {@link CrawlerConfig#setMaxDocuments(int)} for stopping
 * the crawler upon reaching specific event counts. The event counts are only
 * kept for a crawling session.  They are reset to zero upon restarting
 * the crawler.
 * </p>
 * <p>
 * Not specifying any maximum or events has no effect.
 * </p>
 *
 * <h3>Difference with "maxDocuments"</h3>
 * <p>
 * The "maxDocuments" option deals with "processed" documents.  Those are
 * documents that were initially queued for crawling and crawling was attempted
 * on them, whether that exercise what successful or not.  That is,
 * "maxDocuments" will not count documents that were sent to your committer
 * for additions or deletions, but also documents that were rejected
 * by your Importer configuration, produced errors, etc.
 * This class gives you more control over what should trigger a crawler to stop.
 * </p>
 * <p>
 * Note that for this class to take effect, make sure that "maxDocuments" has
 * a high enough number or is set <code>-1</code> (unlimited).
 * </p>
 *
 * <h3>Combining events</h3>
 * <p>
 * If your event matcher matches more than one event, you can decide what
 * should be the expected behavior. Options are:
 * </p>
 * <ul>
 *   <li>
 *     <b>any</b>: Stop the crawler when any of the matching event count
 *     reaches the specified maximum.
 *   </li>
 *   <li>
 *     <b>all</b>: Stop the crawler when all of the matching event counts
 *     have reached the maximum.
 *   </li>
 *   <li>
 *     <b>sum</b>: Stop the crawler when the sum of all matching event counts
 *     have reached the maximum.
 *   </li>
 * </ul>
 *
 * {@nx.xml.usage
 * <listener
 *     class="com.norconex.crawler.core.crawler.event.impl.StopCrawlerOnMaxEventListener"
 *     max="(maximum count)"
 *     onMultiple="[any|all|sum]">
 *   <eventMatcher
 *     {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (event name-matching expression)
 *   </eventMatcher>
 * </listener>
 * }
 *
 * {@nx.xml.example
 * <listener class="StopCrawlerOnMaxEventListener" max="100" onMultiple="sum">
 *   <eventMatcher method="csv">DOCUMENT_COMMITTED_UPSERT,DOCUMENT_COMMITTED_DELETE</eventMatcher>
 * </listener>
 * }
 * <p>
 * The above example will stop the crawler when the sum of committed documents
 * (upserts + deletions) reaches 100.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
public class StopCrawlerOnMaxEventListener
        implements IEventListener<Event>, XMLConfigurable {

    private static final Logger LOG =
            LoggerFactory.getLogger(StopCrawlerOnMaxEventListener.class);

    public enum OnMultiple {
        /**
         * Stop the crawler when any of the matching event count
         * reaches the specified maximum.
         */
        ANY,
        /**
         * Stop the crawler when all of the matching event counts
         * have reached the maximum.
         */
        ALL,
        /**
         * Stop the crawler when the sum of all matching event counts
         * have reached the maximum.
         */
        SUM
    }

    private Map<String, AtomicLong> eventCounts = new ConcurrentHashMap<>();
    private Crawler crawler;

    private final TextMatcher eventMatcher = TextMatcher.regex(null);
    private OnMultiple onMultiple = OnMultiple.ANY;
    private long maximum;

    /**
     * Gets the event matcher used to identify which events will be counted.
     * @return text matcher, never <code>null</code>
     */
    public TextMatcher getEventMatcher() {
        return eventMatcher;
    }
    /**
     * Sets the event matcher used to identify which events will be counted.
     * @param eventMatcher event matcher
     */
    public void setEventMatcher(TextMatcher eventMatcher) {
        this.eventMatcher.copyFrom(eventMatcher);
    }

    public OnMultiple getOnMultiple() {
        return onMultiple;
    }
    public void setOnMultiple(OnMultiple onMultiple) {
        this.onMultiple = onMultiple;
    }

    public long getMaximum() {
        return maximum;
    }
    public void setMaximum(long maximum) {
        this.maximum = maximum;
    }

    @Override
    public void accept(Event event) {
        if (event.is(CrawlerEvent.CRAWLER_RUN_BEGIN)) {
            eventCounts.clear();
            this.crawler = ((CrawlerEvent) event).getSource();
        }

        if (!eventMatcher.matches(event.getName())) {
            return;
        }

        eventCounts.computeIfAbsent(
                event.getName(), k -> new AtomicLong()).incrementAndGet();

        if (isMaxReached()) {
            LOG.info("Maximum number of events reached for crawler: {}",
                    crawler.getId());
            crawler.stop();
        }
    }

    private boolean isMaxReached() {
        if (OnMultiple.ALL == onMultiple) {
            return eventCounts.values().stream()
                    .allMatch(v -> v.get() >= maximum);
        } else if (OnMultiple.SUM == onMultiple) {
            return eventCounts.values().stream().collect(
                    Collectors.summingLong(AtomicLong::get)) >= maximum;
        } else { // ANY
            return eventCounts.values().stream()
                    .anyMatch(v -> v.get() >= maximum);
        }
    }

    @Override
    public void loadFromXML(XML xml) {
        onMultiple = xml.getEnum("@onMultiple", OnMultiple.class, onMultiple);
        maximum = xml.getLong("@maximum", maximum);
        eventMatcher.loadFromXML(xml.getXML("eventMatcher"));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("onMultiple", onMultiple);
        xml.setAttribute("maximum", maximum);
        eventMatcher.saveToXML(xml.addElement("eventMatcher"));
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
