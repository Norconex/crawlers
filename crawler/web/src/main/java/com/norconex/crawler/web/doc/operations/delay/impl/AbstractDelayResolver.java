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
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.web.doc.operations.delay.DelayResolver;
import com.norconex.crawler.web.doc.operations.delay.impl.BaseDelayResolverConfig.DelayResolverScope;
import com.norconex.crawler.web.robot.RobotsTxt;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

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
 * <h3>
 * XML configuration usage:
 * </h3>
 * <p>
 * The following should be shared across concrete implementations
 * (which can add more configurable attributes and tags).
 * </p>
 * {@nx.xml
 * <delay class="(implementing class)"
 *     default="(milliseconds)"
 *     ignoreRobotsCrawlDelay="[false|true]"
 *     scope="[crawler|site|thread]">
 * </delay>
 * }
 * @since 2.5.0
 */
@Slf4j
@EqualsAndHashCode
@ToString
public abstract class AbstractDelayResolver<T extends BaseDelayResolverConfig>
        implements DelayResolver, Configurable<T> {

    @JsonIgnore
    private final Map<DelayResolverScope, AbstractDelay> delays =
            new EnumMap<>(DelayResolverScope.class);

    protected AbstractDelayResolver() {
        delays.put(DelayResolverScope.CRAWLER, new CrawlerDelay());
        delays.put(DelayResolverScope.SITE, new SiteDelay());
        delays.put(DelayResolverScope.THREAD, new ThreadDelay());
    }

    @Override
    public void delay(RobotsTxt robotsTxt, String url) {
        var expectedDelayNanos = getExpectedDelayNanos(robotsTxt, url);
        if (expectedDelayNanos <= 0) {
            return;
        }
        var delay = delays.get(getConfiguration().getScope());
        if (delay == null) {
            LOG.warn(
                    "Unspecified or unsupported delay scope: {}. "
                            + "Using {} scope.",
                    getConfiguration().getScope(),
                    BaseDelayResolverConfig.DEFAULT_SCOPE);
            delay = delays.get(BaseDelayResolverConfig.DEFAULT_SCOPE);
        }
        delay.delay(expectedDelayNanos, url);
    }

    private long getExpectedDelayNanos(
            RobotsTxt robotsTxt, String url) {
        var delayNanos = Optional
                .ofNullable(getConfiguration().getDefaultDelay())
                .orElse(BaseDelayResolverConfig.DEFAULT_DELAY)
                .toNanos();
        if (isUsingRobotsTxtCrawlDelay(robotsTxt)) {
            delayNanos = TimeUnit.SECONDS.toNanos(
                    (long) (robotsTxt.getCrawlDelay()));
        } else {
            var explicitDelay = resolveExplicitDelay(url);
            if (explicitDelay != null) {
                delayNanos = explicitDelay.toNanos();
            }
        }
        return delayNanos;
    }

    /**
     * Resolves explicitly specified delay, in milliseconds.
     * This method is only invoked when there are no delays from robots.txt.
     * If the implementing class does not have a delay resolution, -1
     * or <code>null</code> is returned (the default delay will be used).
     * @param url URL for which to resolve delay
     * @return delay in millisecond, or -1 or <code>null</code>
     */
    protected abstract Duration resolveExplicitDelay(String url);

    private boolean isUsingRobotsTxtCrawlDelay(RobotsTxt robotsTxt) {
        return robotsTxt != null
                && !getConfiguration().isIgnoreRobotsCrawlDelay()
                && robotsTxt.getCrawlDelay() >= 0;
    }
}
