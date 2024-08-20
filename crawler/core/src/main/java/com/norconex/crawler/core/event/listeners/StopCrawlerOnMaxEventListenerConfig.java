/* Copyright 2021-2023 Norconex Inc.
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
package com.norconex.crawler.core.event.listeners;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.CrawlerConfig;

import lombok.Data;
import lombok.experimental.Accessors;

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
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class StopCrawlerOnMaxEventListenerConfig {

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

    private final TextMatcher eventMatcher = new TextMatcher();
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
     * @return this instance
     */
    public StopCrawlerOnMaxEventListenerConfig setEventMatcher(
            TextMatcher eventMatcher) {
        this.eventMatcher.copyFrom(eventMatcher);
        return this;
    }
}
