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
import com.norconex.crawler.core.event.CrawlerEvent;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Provides the ability to send deletion requests to your configured
 * committer(s) whenever a reference is rejected, regardless whether it was
 * encountered in a previous crawling session or not.
 * </p>
 *
 * <h3>Supported events</h3>
 * <p>
 * By default this listener will send deletion requests for all references
 * associated with a {@link CrawlerEvent} name starting with
 * <code>REJECTED_</code>. To avoid performance issues when dealing with
 * too many deletion requests, it is recommended you can change this behavior
 * to match exactly the events you are interested in with
 * {@link #setEventMatcher(TextMatcher)}.
 * Keep limiting events to "rejected" ones to avoid unexpected results.
 * </p>
 *
 * <h3>Deletion requests sent once</h3>
 * <p>
 * This class tries to handles each reference for "rejected" events only once.
 * To do so it will queue all such references and wait until normal
 * crawler completion to send them. Waiting for completion also gives this
 * class a chance to listen for deletion requests sent to your committer as
 * part of the crawler regular execution (typically on subsequent crawls).
 * This helps ensure you do not get duplicate deletion requests for the same
 * reference.
 * </p>
 *
 * <h3>Only references</h3>
 * <p>
 * Since several rejection events are triggered before document are processed,
 * we can't assume there is any metadata attached with rejected
 * references. Be aware this can cause issues if you are using rules in your
 * committer (e.g., to route requests) based on metadata.
 * <p>
 *
 * {@nx.xml.usage
 * <listener
 *     class="com.norconex.crawler.core.crawler.event.impl.DeleteRejectedEventListener">
 *   <eventMatcher
 *     {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (event name-matching expression)
 *   </eventMatcher>
 * </listener>
 * }
 *
 * {@nx.xml.example
 * <listener class="DeleteRejectedEventListener">
 *   <eventMatcher method="csv">REJECTED_NOTFOUND,REJECTED_FILTER</eventMatcher>
 * </listener>
 * }
 * <p>
 * The above example will send deletion requests whenever a reference is not
 * found (e.g., a 404 response from a web server) or if it was filtered out
 * by the crawler.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class DeleteRejectedEventListenerConfig {

    private final TextMatcher eventMatcher = TextMatcher.regex("REJECTED_.*");

    /**
     * Gets the event matcher used to identify which events can trigger
     * a deletion request. Default is regular expression
     * <code>REJECTED_.*</code>.
     * @return text matcher, never <code>null</code>
     */
    public TextMatcher getEventMatcher() {
        return eventMatcher;
    }
    /**
     * Sets the event matcher used to identify which events can trigger
     * a deletion request.
     * @param eventMatcher event matcher
     * @return this instance
     */
    public DeleteRejectedEventListenerConfig setEventMatcher(
            TextMatcher eventMatcher) {
        this.eventMatcher.copyFrom(eventMatcher);
        return this;
    }
}
