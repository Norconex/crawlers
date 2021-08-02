/* Copyright 2018-2020 Norconex Inc.
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
import static java.util.Optional.ofNullable;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;
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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Timeouts;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.doc.HttpCrawlState;
import com.norconex.collector.http.fetch.AbstractHttpFetcher;
import com.norconex.collector.http.fetch.HttpFetchException;
import com.norconex.collector.http.fetch.HttpFetchResponseBuilder;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.fetch.impl.webdriver.HttpSniffer.DriverResponseFilter;
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
 * <h3>HTTP Headers</h3>
 * <p>
 * By default, web drivers do not expose HTTP headers.  If you want to
 * capture them, configure the "httpSniffer". A proxy service
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
 *     (Web driver waits for that long for the page to finish rendering.)
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
 *   <restrictions>
 *     <restrictTo caseSensitive="[false|true]"
 *         field="(name of metadata field name to match)">
 *       (regular expression of value to match)
 *     </restrictTo>
 *     <!-- multiple "restrictTo" tags allowed (only one needs to match) -->
 *   </restrictions>
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
 *   <restrictions>
 *     <restrictTo field="document.reference">.*dynamic.*$</restrictTo>
 *   </restrictions>
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

  //TODO download files?
  //https://www.toolsqa.com/selenium-webdriver/how-to-download-files-using-selenium/

    private static final Logger LOG = LoggerFactory.getLogger(
            WebDriverHttpFetcher.class);

    private final WebDriverHttpFetcherConfig cfg;
    private CachedStreamFactory streamFactory;
    private String userAgent;
    private HttpSniffer httpSniffer;
    private ScreenshotHandler screenshotHandler;
    private WebDriverHolder driverHolder;

    public WebDriverHttpFetcher() {
        this(new WebDriverHttpFetcherConfig());
    }
    public WebDriverHttpFetcher(WebDriverHttpFetcherConfig config) {
        super();
        this.cfg = Objects.requireNonNull(config, "'config' must not be null.");
    }

    public WebDriverHttpFetcherConfig getConfig() {
        return cfg;
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
        if (c != null) {
            streamFactory = c.getStreamFactory();
        } else {
            streamFactory = new CachedStreamFactory();
        }

        this.driverHolder = new WebDriverHolder(cfg);

        if (cfg.getHttpSnifferConfig() != null) {
            LOG.info("Starting {} HTTP sniffer...", cfg.getBrowser());
            httpSniffer = new HttpSniffer();
            httpSniffer.start(
                    driverHolder.getDriverOptions().getValue(),
                    cfg.getHttpSnifferConfig());
            userAgent = cfg.getHttpSnifferConfig().getUserAgent();
        }
    }

    @Override
    protected void fetcherThreadBegin(HttpCrawler crawler) {
        WebDriver driver = driverHolder.getDriver();
        if (StringUtils.isBlank(userAgent)) {
            userAgent = (String) ((JavascriptExecutor) driver).executeScript(
                    "return navigator.userAgent;");
        }
    }
    @Override
    protected void fetcherThreadEnd(HttpCrawler crawler) {
        LOG.info("Shutting down {} web driver.", cfg.getBrowser());
        if (driverHolder != null) {
            driverHolder.releaseDriver();
        }
    }

    @Override
    protected void fetcherShutdown(HttpCollector c) {
        if (httpSniffer != null) {
            LOG.info("Shutting down {} HTTP sniffer...", cfg.getBrowser());
            Sleeper.sleepSeconds(5);
            httpSniffer.stop();
        }
    }

    @Override
    public IHttpFetchResponse fetch(CrawlDoc doc, HttpMethod httpMethod)
            throws HttpFetchException {

        HttpMethod method = ofNullable(httpMethod).orElse(GET);
        if (method != GET) {
            String reason = "HTTP " + httpMethod + " method not supported.";
            if (method == HEAD) {
                reason += " To obtain headers, use GET with a configured "
                        + "'httpSniffer'.";
            }
            return HttpFetchResponseBuilder.unsupported().setReasonPhrase(
                  reason).create();
        }

	    LOG.debug("Fetching document: {}", doc.getReference());

	    if (httpSniffer != null) {
	        httpSniffer.bind(doc.getReference());
	    }

        doc.setInputStream(fetchDocumentContent(doc.getReference()));
        IHttpFetchResponse response = resolveDriverResponse(doc);

        if (screenshotHandler != null) {
            screenshotHandler.takeScreenshot(driverHolder.getDriver(), doc);
        }

        if (response != null) {
            return response;
        }
        return new HttpFetchResponseBuilder()
                .setCrawlState(HttpCrawlState.NEW)
                .setStatusCode(200)
                .setReasonPhrase("No exception thrown, but real status code "
                        + "unknown. Capture headers for real status code.")
                .setUserAgent(getUserAgent())
                .create();
    }

    protected WebDriver getWebDriver() {
        return driverHolder.getDriver();
    }

    // Overwrite to perform more advanced configuration/manipulation.
    // thread-safe
    protected InputStream fetchDocumentContent(String url) {
        WebDriver driver = driverHolder.getDriver();
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

        Timeouts timeouts = driver.manage().timeouts();
        if (cfg.getPageLoadTimeout() != 0) {
            timeouts.pageLoadTimeout(cfg.getPageLoadTimeout(),  MILLISECONDS);
        }
        if (cfg.getImplicitlyWait() != 0) {
            timeouts.implicitlyWait(cfg.getImplicitlyWait(), MILLISECONDS);
        }
        if (cfg.getScriptTimeout() != 0) {
            timeouts.setScriptTimeout(cfg.getScriptTimeout(), MILLISECONDS);
        }

        if (cfg.getWaitForElementTimeout() != 0
                && StringUtils.isNotBlank(cfg.getWaitForElementSelector())) {
            WaitElementType elType = ObjectUtils.defaultIfNull(
                    cfg.getWaitForElementType(), WaitElementType.TAGNAME);
            LOG.debug("Waiting for element '{}' of type '{}' for '{}'.",
                    cfg.getWaitForElementSelector(), elType, url);

            WebDriverWait wait = new WebDriverWait(
                    driver, cfg.getWaitForElementTimeout() / 1000);
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

        String pageSource = driver.getPageSource();
        LOG.debug("Fetched page source length: {}", pageSource.length());
        return IOUtils.toInputStream(pageSource, StandardCharsets.UTF_8);
    }

    private IHttpFetchResponse resolveDriverResponse(CrawlDoc doc) {
        IHttpFetchResponse response = null;
        if (httpSniffer != null) {
            DriverResponseFilter driverResponseFilter = httpSniffer.unbind();
            if (driverResponseFilter != null) {
                for (Entry<String, String> en
                        : driverResponseFilter.getHeaders()) {
                    String name = en.getKey();
                    String value = en.getValue();
                    // Content-Type + Content Encoding (Charset)
                    if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                        ApacheHttpUtil.applyContentTypeAndCharset(
                                value, doc.getDocInfo());
                    }
                    doc.getMetadata().add(name, value);
                }
                response = toFetchResponse(driverResponseFilter);
            }
        }

        //TODO we assume text/html as default until WebDriver expands its API
        // to obtain it.
        if (doc.getDocInfo().getContentType() == null) {
            doc.getDocInfo().setContentType(ContentType.HTML);
        }

        return response;
    }

    private IHttpFetchResponse toFetchResponse(
            DriverResponseFilter driverResponseFilter) {
        IHttpFetchResponse response = null;
        if (driverResponseFilter != null) {
            //TODO validate status code
            int statusCode = driverResponseFilter.getStatusCode();
            String reason = driverResponseFilter.getReasonPhrase();

            HttpFetchResponseBuilder b = new HttpFetchResponseBuilder()
                    .setStatusCode(statusCode)
                    .setReasonPhrase(reason)
                    .setUserAgent(getUserAgent());
            if (statusCode >= 200 && statusCode < 300) {
                response = b.setCrawlState(CrawlState.NEW).create();
            } else {
                response = b.setCrawlState(CrawlState.BAD_STATUS).create();
            }
        }
        return response;
    }

    @Override
    public void loadHttpFetcherFromXML(XML xml) {
        xml.populate(cfg);
        xml.ifXML("screenshot", x -> {
            ScreenshotHandler h =
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