/* Copyright 2020-2025 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl.webdriver;

import java.awt.Dimension;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.openqa.selenium.By;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.crawler.core.fetch.BaseFetcherConfig;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link WebDriverFetcher}.
 * </p>
 * @see WebDriverFetcher
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class WebDriverFetcherConfig extends BaseFetcherConfig {

    public enum WaitElementType {
        TAGNAME(By::tagName),
        CLASSNAME(By::className),
        CSSSELECTOR(By::cssSelector),
        ID(By::id),
        LINKTEXT(By::linkText),
        NAME(By::name),
        PARTIALLINKTEXT(By::partialLinkText),
        XPATH(By::xpath);

        private final Function<String, By> byFunction;

        WaitElementType(Function<String, By> byFunction) {
            this.byFunction = byFunction;
        }

        By getBy(String selector) {
            return byFunction.apply(selector);
        }
    }

    /**
     * The browser used for crawling. Also defines which WebDriver to use.
     * Default is Firefox.
     */
    private Browser browser = Browser.FIREFOX;
    /**
     * Local path to driver executable or {@code null} to attempt
     * automatic detection of the driver path.
     * See web driver vendor documentation for the location facilitating
     * detection.
     * Use {@link #setRemoteURL(URL)} instead when using
     * a remote web driver cluster.
     */
    private Path driverPath;
    /**
     * Local path to browser executable or {@code null} to attempt
     * automatic browser path detection. See browser vendor documentation
     * for the expected browser installed location.
     * Use {@link #setRemoteURL(URL)} instead when using
     * a remote web driver cluster.
     */
    private Path browserPath;
    /**
     * URL of a remote WebDriver cluster. Alternative to using a local
     * browser and local web driver.
     */
    private URL remoteURL;

    /**
     * <b>Experimental</b> feature where the selected {@link Browser} will be
     * used to configure and use
     * <a href="https://github.com/SeleniumHQ/htmlunit-driver">
     * HtmlUnit WebDriver</a> as a wrapper instead of directly using the
     * browser WebDriver.  Not all browsers are supported by HtmlUnit
     * (Chrome, Firefox, and Edge are, as of this writing).
     */
    private boolean useHtmlUnit;

    /**
     * Optionally setup an HTTP proxy that allows to set and capture HTTP
     * headers. For advanced use only.
     */
    private HttpSniffer httpSniffer;

    /**
     * When configured, takes screenshots of each web pages.
     */
    private ScreenshotHandler screenshotHandler;

    /**
     * Optional capabilities (configuration options) for the web driver.
     * Many are specific to each browser or web driver. Refer to vendor
     * documentation.
     */
    private final Map<String, String> capabilities = new HashMap<>();
    /**
     * Optional command-line arguments supported by some web driver or browser.
     */
    private final List<String> arguments = new ArrayList<>();

    /**
     * Optionally set the browser window dimensions. E.g., 640x480.
     */
    private Dimension windowSize;

    /**
     * Optional JavaScript code to be run the moment a page is requested.
     */
    private String earlyPageScript;
    /**
     * Optional JavaScript code to be run after we are done waiting for a page.
     */
    private String latePageScript;

    /**
     * Web driver max wait time for a page to load.
     */
    private Duration pageLoadTimeout;
    /**
     * Web driver max wait time for an element to appear. See
     * {@link #getWaitForElementSelector()}.
     */
    private Duration implicitlyWait;
    /**
     * Web driver max wait time for a scripts to execute.
     */
    private Duration scriptTimeout;
    /**
     * Makes the current thread sleep for the specified duration, to
     * give the web driver enough time to load the page.
     * Sometimes necessary for some web driver implementations when preferable
     * options fail.
     */
    private Duration threadWait;

    /**
     * The type of reference to use when waiting for an element.
     */
    private WaitElementType waitForElementType;
    /**
     * Reference to an element to wait for. The nature of the reference itself
     * is defined by {@link #getWaitForElementType()}.
     */
    private String waitForElementSelector;
    /**
     * Max wait time for an element to show up in browser before returning.
     * Default 'type' is 'tagName'.
     */
    private Duration waitForElementTimeout;

    /**
     * A maximum number of navigations at which we restart the browser. Applies
     * to individual browser instances, typically one per crawl threads.
     * After a while, browsers can accumulate a lot of data in memory and
     * may start performing poorly or cause other issues. Restarting
     * may help.
     * Default is {@code -1} for unlimited navigation (no restart)
     */
    private int browserMaxNavigations;
    /**
     * A maximum duration at which we restart the browser. Applies
     * to individual browser instances, typically one per crawl threads.
     * After a while, browsers can accumulate a lot of data in memory and
     * may start performing poorly or cause other issues. Restarting
     * may help.
     * Default is {@code null} (no restart)
     */
    private Duration browserMaxAge;
    /**
     * Amount of time between each check for unused browser instances and
     * closing them.
     * Defaults to 30 seconds. Cannot be {@code null}.
     */
    @NonNull
    private Duration cleanupInterval = Duration.ofSeconds(30);

    /**
     * Gets optional capabilities (configuration options) for the web driver.
     * Many are specific to each browser or web driver. Refer to vendor
     * documentation.
     * @return capabilities
     */
    public Map<String, String> getCapabilities() {
        return Collections.unmodifiableMap(capabilities);
    }

    /**
     * Sets optional capabilities (configuration options) for the web driver.
     * Many are specific to each browser or web driver. Refer to vendor
     * documentation.
     * @param capabilities web driver capabilities
     * @return this
     */
    public WebDriverFetcherConfig setCapabilities(
            Map<String, String> capabilities) {
        CollectionUtil.setAll(this.capabilities, capabilities);
        return this;
    }
}
