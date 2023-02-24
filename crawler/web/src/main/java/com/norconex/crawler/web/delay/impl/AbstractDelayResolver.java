/* Copyright 2016-2023 Norconex Inc.
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
package com.norconex.crawler.web.delay.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.web.delay.DelayResolver;
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
public abstract class AbstractDelayResolver
        implements DelayResolver, XMLConfigurable {

    public static final String SCOPE_CRAWLER = "crawler";
    public static final String SCOPE_SITE = "site";
    public static final String SCOPE_THREAD = "thread";

    /** Default delay is 3 seconds. */
    public static final long DEFAULT_DELAY = 3000;

    private final Map<String, AbstractDelay> delays = new HashMap<>();
    private long defaultDelay = DEFAULT_DELAY;
    private boolean ignoreRobotsCrawlDelay = false;
    private String scope = SCOPE_CRAWLER;

    protected AbstractDelayResolver() {
        delays.put(SCOPE_CRAWLER, new CrawlerDelay());
        delays.put(SCOPE_SITE, new SiteDelay());
        delays.put(SCOPE_THREAD, new ThreadDelay());
    }

    @Override
    public void delay(RobotsTxt robotsTxt, String url) {
        var expectedDelayNanos = getExpectedDelayNanos(robotsTxt, url);
        if (expectedDelayNanos <= 0) {
            return;
        }
        var delay = delays.get(scope);
        if (delay == null) {
            LOG.warn("Unspecified or unsupported delay scope: "
                    + scope + ".  Using crawler scope.");
            delay = delays.get(SCOPE_CRAWLER);
        }
        delay.delay(expectedDelayNanos, url);
    }

    /**
     * Gets the default delay in milliseconds.
     * @return default delay
     */
    public long getDefaultDelay() {
        return defaultDelay;
    }
    /**
     * Sets the default delay in milliseconds.
     * @param defaultDelay default deleay
     */
    public void setDefaultDelay(long defaultDelay) {
        this.defaultDelay = defaultDelay;
    }

    /**
     * Gets whether to ignore crawl delays specified in a site robots.txt
     * file.  Not applicable when robots.txt are ignored.
     * @return <code>true</code> if ignoring robots.txt crawl delay
     */
    public boolean isIgnoreRobotsCrawlDelay() {
        return ignoreRobotsCrawlDelay;
    }
    /**
     * Sets whether to ignore crawl delays specified in a site robots.txt
     * file.  Not applicable when robots.txt are ignored.
     * @param ignoreRobotsCrawlDelay <code>true</code> if ignoring
     *            robots.txt crawl delay
     */
    public void setIgnoreRobotsCrawlDelay(boolean ignoreRobotsCrawlDelay) {
        this.ignoreRobotsCrawlDelay = ignoreRobotsCrawlDelay;
    }

    /**
     * Gets the delay scope.
     * @return delay scope
     */
    public String getScope() {
        return scope;
    }
    /**
     * Sets the delay scope.
     * @param scope one of "crawler", "site", or "thread".
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    private long getExpectedDelayNanos(
            RobotsTxt robotsTxt, String url) {
        var delayNanos = millisToNanos(defaultDelay);
        if (isUsingRobotsTxtCrawlDelay(robotsTxt)) {
            delayNanos = TimeUnit.SECONDS.toNanos(
                    (long)(robotsTxt.getCrawlDelay()));
        } else {
            var explicitDelay = resolveExplicitDelay(url);
            if (explicitDelay > -1) {
                delayNanos = millisToNanos(explicitDelay);
            }
        }
        return delayNanos;
    }

    /**
     * Resolves explicitly specified delay, in milliseconds.
     * This method is only invoked when there are no delays from robots.txt.
     * If the implementing class does not have a delay resolution, -1 is
     * returned (the default delay will be used).
     * @param url URL for which to resolve delay
     * @return delay in millisecond, or -1
     */
    protected abstract long resolveExplicitDelay(String url);

    private boolean isUsingRobotsTxtCrawlDelay(RobotsTxt robotsTxt) {
        return robotsTxt != null && !ignoreRobotsCrawlDelay
                && robotsTxt.getCrawlDelay() >= 0;
    }

    private long millisToNanos(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }


    @Override
    public final void loadFromXML(XML xml) {
        defaultDelay = xml.getDurationMillis("@default", defaultDelay);
        ignoreRobotsCrawlDelay = xml.getBoolean(
                "@ignoreRobotsCrawlDelay", ignoreRobotsCrawlDelay);
        scope = xml.getString("@scope", SCOPE_CRAWLER);
        loadDelaysFromXML(xml);
    }

    /**
     * Loads explicit configuration of delays form XML.  Implementors should
     * override this method if they wish to add extra configurable elements.
     * Default implementation does nothing.
     * @param xml configuration
     */
    protected void loadDelaysFromXML(XML xml) {
        //noop
    }

    @Override
    public final void saveToXML(XML xml) {
        xml.setAttribute("default", Long.toString(defaultDelay));
        xml.setAttribute("scope", scope);
        xml.setAttribute("ignoreRobotsCrawlDelay", ignoreRobotsCrawlDelay);
        saveDelaysToXML(xml);
    }

    /**
     * Saves explicit configuration of delays to XML.  Implementors should
     * override this method if they wish to add extra configurable elements.
     * Default implementation does nothing.
     * @param xml XML
     */
    protected void saveDelaysToXML(XML xml) {
        //noop
    }
}
