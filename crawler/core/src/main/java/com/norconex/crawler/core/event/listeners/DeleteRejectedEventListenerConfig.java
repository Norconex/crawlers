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
 * Configuration for {@link DeleteRejectedEventListener}.
 * </p>
 */
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
