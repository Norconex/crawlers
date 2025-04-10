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
package com.norconex.crawler.web.doc.operations.delay.impl;

import java.time.Duration;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Base implementation for creating voluntary delays between URL downloads.
 * This base class offers a few ways the actual delay value can be defined
 * (in order):
 * </p>
 * <ol>
 *   <li>Takes the delay specify by a robots.txt file.
 *       Only applicable if robots.txt files and its robots crawl delays
 *       are not ignored.</li>
 *   <li>Takes an explicitly specified delay, as per implementing class.</li>
 *   <li>Use the specified default delay or 3 seconds, if none is
 *       specified.</li>
 * </ol>
 * <p>
 * One of these following scope dictates how the delay is applied, listed
 * in order from the best behaved to the least.
 * </p>
 * <ul>
 *   <li><b>CRAWLER</b>: the delay is applied between each URL download
 *       within a crawler instance, regardless how many threads are defined
 *       within that crawler, or whether URLs are from the
 *       same site or not.  This is the default scope.</li>
 *   <li><b>SITE</b>: the delay is applied between each URL download
 *       from the same site within a crawler instance, regardless how many
 *       threads are defined. A site is defined by a URL protocol and its
 *       domain (e.g. http://example.com).</li>
 *   <li><b>THREAD</b>: the delay is applied between each URL download from
 *       any given thread.  The more threads you have the less of an
 *       impact the delay will have.</li>
 * </ul>
 * @since 2.5.0
 */
@Data
@Accessors(chain = true)
public class BaseDelayResolverConfig {

    public enum DelayResolverScope {
        CRAWLER, SITE, THREAD
    }

    /** Default delay is 3 seconds. */
    public static final Duration DEFAULT_DELAY = Duration.ofSeconds(3);
    public static final DelayResolverScope DEFAULT_SCOPE =
            DelayResolverScope.CRAWLER;

    /**
     * The default delay.
     */
    private Duration defaultDelay = DEFAULT_DELAY;

    /**
     * Whether to ignore crawl delays specified in a site robots.txt
     * file. Not applicable when robots.txt are ignored.
     */
    private boolean ignoreRobotsCrawlDelay = false;

    /**
     * Gets the delay scope. See class documentation for a description
     * of supported scopes.
     */
    private DelayResolverScope scope = DEFAULT_SCOPE;
}
