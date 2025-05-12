/* Copyright 2021-2024 Norconex Inc.
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

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link StopCrawlerOnMaxEventListener}.
 * </p>
 */
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
    /**
     * The maximum number of events matching the event matcher on which
     * we stop the crawler.
     */
    private long maximum;

    /**
     * Gets the event matcher used to identify which events will be counted.
     * @return text matcher, never {@code null}
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
