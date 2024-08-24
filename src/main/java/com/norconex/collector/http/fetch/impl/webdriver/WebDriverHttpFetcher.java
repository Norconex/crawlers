/* Copyright 2018-2021 Norconex Inc.
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
package com.norconex.collector.http.fetch.impl.webdriver;

import static com.norconex.collector.http.fetch.HttpMethod.GET;
import static com.norconex.collector.http.fetch.HttpMethod.HEAD;
import static java.time.Duration.ofMillis;
import static java.util.Optional.ofNullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.HttpHeaders;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.fetch.AbstractHttpFetcher;
import com.norconex.collector.http.fetch.HttpFetchException;
import com.norconex.collector.http.fetch.HttpFetchResponseBuilder;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.fetch.impl.webdriver.HttpSniffer.SniffedResponseHeader;
import com.norconex.collector.http.fetch.impl.webdriver.WebDriverHttpFetcherConfig.WaitElementType;
import com.norconex.collector.http.fetch.util.ApacheHttpUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Uses Selenium WebDriver support for using native browsers to crawl documents.
 * Useful for crawling JavaScript-driven websites.
 * </p>
 *
 * <h3>Considerations</h3>
 * <p>
 * Relying on an external software to fetch pages can be slower and not as
 * scalable and may be less stable. Downloading of binaries and non-HTML file
 * format may not always be possible. The use of {@link GenericHttpFetcher}
 * should be preferred whenever possible. Use at your own risk.
 * </p>
 *
 * <h3>Supported HTTP method</h3>
 * <p>
 * This fetcher only supports HTTP GET method.
 * </p>
 *
 * <h3>HTTP Headers</h3>
 * <p>
 * By default, web drivers do not expose HTTP headers from HTTP GET request.
 * If you want to capture them, configure the "httpSniffer". A proxy service
 * will be started to monitor HTTP traffic and store HTTP headers.
 * </p>
 * <p>
 * <b>NOTE:</b> Capturing headers with a proxy may not be supported by all
 * Browsers/WebDriver implementations.
 * </p>
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.collector.http.fetch.impl.webdriver.WebDriverHttpFetcher">
 *
 *   <browser>[chrome|edge|firefox|opera|safari]</browser>
 *
 *   <!-- Local web driver settings -->
 *   <browserPath>(browser executable or blank to detect)</browserPath>
 *   <driverPath>(driver executable or blank to detect)</driverPath>
 *
 *   <!-- Remote web driver setting -->
 *   <remoteURL>(URL of the remote web driver cluster)</remoteURL>
 *
 *   <!-- Optional browser capabilities supported by the web driver. -->
 *   <capabilities>
 *     <capability name="(capability name)">(capability value)</capability>
 *     <!-- multiple "capability" tags allowed -->
 *   </capabilities>
 *
 *   <!-- Optional browser arguments for web drivers supporting them. -->
 *   <arguments>
 *     <arg>(argument value)</arg>
 *     <!-- multiple "arg" tags allowed -->
 *   </arguments>
 *
 *   <!-- Optionally take screenshots of each web pages. -->
 *   <screenshot>
 *     {@nx.include com.norconex.collector.http.fetch.impl.webdriver.ScreenshotHandler@nx.xml.usage}
 *   </screenshot>
 *
 *   <windowSize>(Optional. Browser window dimensions. E.g., 640x480)</windowSize>
 *
 *   <earlyPageScript>
 *     (Optional JavaScript code to be run the moment a page is requested.)
 *   </earlyPageScript>
 *   <latePageScript>
 *     (Optional JavaScript code to be run after we are done
 *      waiting for a page.)
 *   </latePageScript>
 *
 *   <!-- The following timeouts/waits are set in milliseconds or
 *      - human-readable format (English). Default is zero (not set).
 *      -->
 *   <pageLoadTimeout>
 *     (Web driver max wait time for a page to load.)
 *   </pageLoadTimeout>
 *   <implicitlyWait>
 *     (Web driver max wait time for an element to appear. See
 *      "waitForElement".)
 *   </implicitlyWait>
 *   <scriptTimeout>
 *     (Web driver max wait time for a scripts to execute.)
 *   </scriptTimeout>
 *   <waitForElement
 *       type="[tagName|className|cssSelector|id|linkText|name|partialLinkText|xpath]"
 *       selector="(Reference to element, as per the type specified.)">
 *     (Max wait time for an element to show up in browser before returning.
 *      Default 'type' is 'tagName'.)
 *   </waitForElement>
 *   <threadWait>
 *     (Makes the current thread sleep for the specified duration, to
 *     give the web driver enough time to load the page.
 *     Sometimes necessary for some web driver implementations if the above
 *     options do not work.)
 *   </threadWait>
 *
 *   {@nx.include com.norconex.collector.http.fetch.AbstractHttpFetcher#referenceFilters}
 *
 *   <!-- Optionally setup an HTTP proxy that allows to set and capture
 *        HTTP headers. For advanced use only. Not recommended
 *        for regular usage. -->
 *   <httpSniffer>
 *     {@nx.include com.norconex.collector.http.fetch.impl.webdriver.HttpSnifferConfig@nx.xml.usage}
 *   </httpSniffer>
 *
 * </fetcher>
 * }
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.collector.http.fetch.impl.webdriver.WebDriverHttpFetcher">
 *   <browser>firefox</browser>
 *   <driverPath>/drivers/geckodriver.exe</driverPath>
 *   <referenceFilters>
 *     <filter class="ReferenceFilter">
 *       <valueMatcher method="regex">.*dynamic.*$</valueMatcher>
 *     </filter>
 *   </referenceFilters>
 * </fetcher>
 * }
 *
 * <p>The above example will use Firefox to crawl dynamically generated
 * pages using a specific web driver.
 * </p>
 *
 * @author Pascal Essiembre
 * @since 3.0.0
 */
@SuppressWarnings("javadoc")
public class WebDriverHttpFetcher extends AbstractHttpFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(
            WebDriverHttpFetcher.class);

    //--- Set at construction time ---
    private final WebDriverHttpFetcherConfig cfg;

    //--- Set on fetcher start-up ---
    private Browser browser;
    private String userAgent; // set per fetcher requests if not set on start-up
    private HttpSniffer httpSniffer;
    private ScreenshotHandler screenshotHandler;
    private WebDriverLocation location;
    private CachedStreamFactory streamFactory;
    // Resolved capabilities of configured browser. Reused by all driver
    // instances created.
    private MutableCapabilities options;

    //--- Set on fetcher request ---

    // We need to make WebDrivers thread-safe
    private static final ThreadLocal<WebDriver> THREADED_DRIVER = new ThreadLocal<>();


    /**
     * Creates a new WebDriver HTTP Fetcher defaulting to Firefox.
     */
    public WebDriverHttpFetcher() {
        this(new WebDriverHttpFetcherConfig());
    }
    /**
     * Creates a new WebDriver HTTP Fetcher for the supplied configuration.
     */
    public WebDriverHttpFetcher(WebDriverHttpFetcherConfig config) {
        cfg = Objects.requireNonNull(config, "'config' must not be null.");
    }

    public WebDriverHttpFetcherConfig getConfig() {
        return cfg;
    }

    @Override
    protected boolean accept(HttpMethod httpMethod) {
        return HttpMethod.GET.is(httpMethod);
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }

    public ScreenshotHandler getScreenshotHandler() {
        return screenshotHandler;
    }
    public void setScreenshotHandler(
            ScreenshotHandler screenshotHandler) {
        this.screenshotHandler = screenshotHandler;
    }

    @Override
    protected void fetcherStartup(HttpCollector c) {
        LOG.info("Starting WebDriver HTTP fetcher...");
        browser = cfg.getBrowser();
        if (c != null) {
            streamFactory = c.getStreamFactory();
        } else {
            streamFactory = new CachedStreamFactory();
        }
        location = new WebDriverLocation(
                cfg.getDriverPath(),
                cfg.getBrowserPath(),
                cfg.getRemoteURL());

        options = browser.createOptions(location);
        if (cfg.getHttpSnifferConfig() != null) {
            LOG.info("Starting {} HTTP sniffer...", browser);
            httpSniffer = new HttpSniffer();
            httpSniffer.start(options, cfg.getHttpSnifferConfig());
            userAgent = cfg.getHttpSnifferConfig().getUserAgent();
        }
        options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
        options = options.merge(cfg.getCapabilities());
        // add arguments to drivers supporting it
        if (options instanceof FirefoxOptions) {
            ((FirefoxOptions) options).addArguments(cfg.getArguments());
        } else if (options instanceof ChromeOptions) {
            ((ChromeOptions) options).addArguments(cfg.getArguments());
        } else if (options instanceof EdgeOptions) {
            ((EdgeOptions) options).addArguments(cfg.getArguments());
        }
    }

    @Override
    protected void fetcherThreadBegin(HttpCrawler crawler) {
        LOG.info("Creating {} web driver.", browser);
        var driver = browser.createDriver(location, options);
        if (StringUtils.isBlank(userAgent)) {
            userAgent = (String) ((JavascriptExecutor) driver).executeScript(
                    "return navigator.userAgent;");
        }
        THREADED_DRIVER.set(driver);
    }

    @Override
    public IHttpFetchResponse fetch(CrawlDoc doc, HttpMethod httpMethod)
            throws HttpFetchException {
        var method = ofNullable(httpMethod).orElse(GET);
        if (method != GET) {
            var reason = "HTTP " + httpMethod + " method not supported.";
            if (method == HEAD) {
                reason += " To obtain headers, use GET with a configured "
                        + "'httpSniffer'.";
            }
            return HttpFetchResponseBuilder
                    .unsupported()
                    .setReasonPhrase(reason)
                    .create();
        }
	    LOG.debug("Fetching document: {}", doc.getReference());

	    SniffedResponseHeader sniffedResponse = null;
	    if (httpSniffer != null) {
	        sniffedResponse = httpSniffer.track(doc.getReference());
	    }

        doc.setInputStream(fetchDocumentContent(doc.getReference()));

        var fetchResponse = resolveDriverResponse(doc, sniffedResponse);

        if (httpSniffer != null) {
            httpSniffer.untrack(doc.getReference());
        }

        if (screenshotHandler != null) {
            screenshotHandler.takeScreenshot(THREADED_DRIVER.get(), doc);
        }

        if (fetchResponse != null) {
            return fetchResponse;
        }
        return new HttpFetchResponseBuilder()
                .setCrawlState(CrawlState.NEW)
                .setStatusCode(200)
                .setReasonPhrase("No exception thrown, but real status code "
                        + "unknown. Capture headers for real status code.")
                .setUserAgent(getUserAgent())
                .create();
    }

    @Override
    protected void fetcherThreadEnd(HttpCrawler crawler) {
        LOG.info("Shutting down {} web driver.", browser);
        var driver = THREADED_DRIVER.get();
        if (driver != null) {
            driver.quit();
        }
        THREADED_DRIVER.remove();
    }

    @Override
    protected void fetcherShutdown(HttpCollector c) {
        if (httpSniffer != null) {
            LOG.info("Shutting down {} HTTP sniffer...", browser);
            Sleeper.sleepSeconds(5);
            httpSniffer.stop();
        }
    }

    /**
     * Gets the web driver associated with the current thread (if any).
     * @return web driver or <code>null</code>
     */
    protected WebDriver getWebDriver() {
        return THREADED_DRIVER.get();
    }

    // Overwrite to perform more advanced configuration/manipulation.
    // thread-safe
    protected InputStream fetchDocumentContent(String url) {
        var driver = THREADED_DRIVER.get();
        driver.get(url);

        if (StringUtils.isNotBlank(cfg.getEarlyPageScript())) {
            ((JavascriptExecutor) driver).executeScript(
                    cfg.getEarlyPageScript());
        }

        if (cfg.getWindowSize() != null) {
            driver.manage().window().setSize(
                    new org.openqa.selenium.Dimension(
                            cfg.getWindowSize().width,
                            cfg.getWindowSize().height));
        }

        var timeouts = driver.manage().timeouts();
        if (cfg.getPageLoadTimeout() != 0) {
            timeouts.pageLoadTimeout(ofMillis(cfg.getPageLoadTimeout()));
        }
        if (cfg.getImplicitlyWait() != 0) {
            timeouts.implicitlyWait(ofMillis(cfg.getImplicitlyWait()));
        }
        if (cfg.getScriptTimeout() != 0) {
            timeouts.scriptTimeout(ofMillis(cfg.getScriptTimeout()));
        }

        if (cfg.getWaitForElementTimeout() != 0
                && StringUtils.isNotBlank(cfg.getWaitForElementSelector())) {
            var elType = ObjectUtils.defaultIfNull(
                    cfg.getWaitForElementType(), WaitElementType.TAGNAME);
            LOG.debug("Waiting for element '{}' of type '{}' for '{}'.",
                    cfg.getWaitForElementSelector(), elType, url);

            var wait = new WebDriverWait(
                    driver, ofMillis(cfg.getWaitForElementTimeout()));
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    elType.getBy(cfg.getWaitForElementSelector())));

            LOG.debug("Done waiting for element '{}' of type '{}' for '{}'.",
                    cfg.getWaitForElementSelector(), elType, url);
        }

        if (StringUtils.isNotBlank(cfg.getLatePageScript())) {
            ((JavascriptExecutor) driver).executeScript(
                    cfg.getLatePageScript());
        }

        if (cfg.getThreadWait() != 0) {
            Sleeper.sleepMillis(cfg.getThreadWait());
        }

        var pageSource = driver.getPageSource();

        LOG.debug("Fetched page source length: {}", pageSource.length());
        return IOUtils.toInputStream(pageSource, StandardCharsets.UTF_8);
    }

    private IHttpFetchResponse resolveDriverResponse(
            CrawlDoc doc, SniffedResponseHeader sniffedResponse) {

        IHttpFetchResponse response = null;
        if (sniffedResponse != null) {
            sniffedResponse.getHeaders().asMap().forEach((k, v) -> {
                // Content-Type + Content Encoding (Charset)
                var values = (List<String>) v;
                // Content-Type + Content Encoding (Charset)
                if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(k)
                        && !values.isEmpty()) {
                    ApacheHttpUtil.applyContentTypeAndCharset(
                            values.get(0), doc.getDocInfo());
                }
                doc.getMetadata().addList(k, values);
            });

            var statusCode = sniffedResponse.getStatusCode();
            var b = new HttpFetchResponseBuilder()
                    .setStatusCode(statusCode)
                    .setReasonPhrase(sniffedResponse.getReasonPhrase())
                    .setUserAgent(getUserAgent());
            if (statusCode >= 200 && statusCode < 300) {
                response = b.setCrawlState(CrawlState.NEW).create();
            } else {
                response = b.setCrawlState(CrawlState.BAD_STATUS).create();
            }
        }

        //TODO we assume text/html as default until WebDriver expands its API
        // to obtain different types of files.
        if (doc.getDocInfo().getContentType() == null) {
            doc.getDocInfo().setContentType(ContentType.HTML);
        }

        return response;
    }

    @Override
    public void loadHttpFetcherFromXML(XML xml) {
        xml.populate(cfg);
        xml.ifXML("screenshot", x -> {
            var h =
                    new ScreenshotHandler(streamFactory);
            x.populate(h);
            setScreenshotHandler(h);
        });
    }
    @Override
    public void saveHttpFetcherToXML(XML xml) {
        cfg.saveToXML(xml);
        if (screenshotHandler != null) {
            screenshotHandler.saveToXML(xml.addElement("screenshot"));
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
}