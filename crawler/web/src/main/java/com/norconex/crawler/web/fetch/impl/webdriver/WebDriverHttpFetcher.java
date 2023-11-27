/* Copyright 2018-2023 Norconex Inc.
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

import static java.time.Duration.ofMillis;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.fetch.AbstractFetcher;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetchResponse;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcher;
import com.norconex.crawler.web.fetch.impl.webdriver.HttpSniffer.DriverResponseFilter;
import com.norconex.crawler.web.fetch.impl.webdriver.WebDriverHttpFetcherConfig.WaitElementType;
import com.norconex.crawler.web.fetch.util.ApacheHttpUtil;
import com.norconex.importer.doc.Doc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

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
 * <fetcher class="com.norconex.crawler.web.fetch.impl.webdriver.WebDriverHttpFetcher">
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
 *     {@nx.include com.norconex.crawler.web.fetch.impl.webdriver.ScreenshotHandler@nx.xml.usage}
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
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 *
 *   <!-- Optionally setup an HTTP proxy that allows to set and capture
 *        HTTP headers. For advanced use only. Not recommended
 *        for regular usage. -->
 *   <httpSniffer>
 *     {@nx.include com.norconex.crawler.web.fetch.impl.webdriver.HttpSnifferConfig@nx.xml.usage}
 *   </httpSniffer>
 *
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <fetcher class="com.norconex.crawler.web.fetch.impl.webdriver.WebDriverHttpFetcher">
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
 * @since 3.0.0
 */
@SuppressWarnings("javadoc")
@Slf4j
@EqualsAndHashCode
@ToString
public class WebDriverHttpFetcher
        extends AbstractFetcher<
                HttpFetchRequest, HttpFetchResponse, WebDriverHttpFetcherConfig>
        implements HttpFetcher {

    @Getter
    private final WebDriverHttpFetcherConfig configuration =
            new WebDriverHttpFetcherConfig();
    private CachedStreamFactory streamFactory;
    private String userAgent;
//    private HttpSniffer httpSniffer;
//    private ScreenshotHandler screenshotHandler;
    private WebDriverHolder driverHolder;

    @Override
    protected boolean acceptRequest(@NonNull HttpFetchRequest req) {
        return HttpMethod.GET.is(req.getMethod());
    }

    @Override
    public HttpFetchResponse fetch(HttpFetchRequest req )
            throws FetchException {
        var method = Optional.ofNullable(
                req.getMethod()).orElse(HttpMethod.GET);
        var doc = req.getDoc();
        if (method != HttpMethod.GET) {
            var reason = "HTTP " + method + " method not supported.";
            if (method == HttpMethod.HEAD) {
                reason += " To obtain headers, use GET with a configured "
                        + "'httpSniffer'.";
            }
            return GenericHttpFetchResponse.builder()
                    .crawlDocState(CrawlDocState.UNSUPPORTED)
                    .reasonPhrase(reason)
                    .statusCode(-1)
                    .build();
        }

        LOG.debug("Fetching document: {}", doc.getReference());

        if (configuration.getHttpSniffer() != null) {
            configuration.getHttpSniffer().bind(doc.getReference());
        }

        doc.setInputStream(fetchDocumentContent(doc.getReference()));
        var response = resolveDriverResponse(doc);

        if (configuration.getScreenshotHandler() != null) {
            configuration.getScreenshotHandler().takeScreenshot(
                    driverHolder.getDriver(), doc);
        }

        if (response != null) {
            return response;
        }

        return GenericHttpFetchResponse.builder()
                .crawlDocState(CrawlDocState.NEW)
                .statusCode(200)
                .reasonPhrase("No exception thrown, but real status code "
                        + "unknown. Capture headers for real status code.")
                .userAgent(getUserAgent())
                .build();
    }

//    @Override
    public String getUserAgent() {
        return userAgent;
    }

//    public ScreenshotHandler getScreenshotHandler() {
//        return screenshotHandler;
//    }
//    public void setScreenshotHandler(
//            ScreenshotHandler screenshotHandler) {
//        this.screenshotHandler = screenshotHandler;
//    }

    @Override
    protected void fetcherStartup(CrawlSession c) {
        if (c != null) {
            streamFactory = c.getStreamFactory();
        } else {
            streamFactory = new CachedStreamFactory();
        }

        driverHolder = new WebDriverHolder(configuration);

        if (configuration.getHttpSniffer() != null) {
            LOG.info("Starting {} HTTP sniffer...", configuration.getBrowser());
            configuration.getHttpSniffer().start(
                    driverHolder.getDriverOptions().getValue());
            userAgent = configuration.getHttpSniffer()
                    .getConfiguration().getUserAgent();
        }
    }

    @Override
    protected void fetcherThreadBegin(Crawler crawler) {
        var driver = driverHolder.getDriver();
        if (StringUtils.isBlank(userAgent)) {
            userAgent = (String) ((JavascriptExecutor) driver).executeScript(
                    "return navigator.userAgent;");
        }
    }
    @Override
    protected void fetcherThreadEnd(Crawler crawler) {
        LOG.info("Shutting down {} web driver.", configuration.getBrowser());
        if (driverHolder != null) {
            driverHolder.releaseDriver();
        }
    }

    @Override
    protected void fetcherShutdown(CrawlSession c) {
        if (configuration.getHttpSniffer() != null) {
            LOG.info("Shutting down {} HTTP sniffer...",
                    configuration.getBrowser());
            Sleeper.sleepSeconds(5);
            configuration.getHttpSniffer().stop();
        }
    }

    protected WebDriver getWebDriver() {
        return driverHolder.getDriver();
    }

    // Overwrite to perform more advanced configuration/manipulation.
    // thread-safe
    protected InputStream fetchDocumentContent(String url) {
        var driver = driverHolder.getDriver();
        driver.get(url);

        if (StringUtils.isNotBlank(configuration.getEarlyPageScript())) {
            ((JavascriptExecutor) driver).executeScript(
                    configuration.getEarlyPageScript());
        }

        if (configuration.getWindowSize() != null) {
            driver.manage().window().setSize(
                    new org.openqa.selenium.Dimension(
                            configuration.getWindowSize().width,
                            configuration.getWindowSize().height));
        }

        var timeouts = driver.manage().timeouts();
        if (configuration.getPageLoadTimeout() != 0) {
            timeouts.pageLoadTimeout(
                    ofMillis(configuration.getPageLoadTimeout()));
        }
        if (configuration.getImplicitlyWait() != 0) {
            timeouts.implicitlyWait(
                    ofMillis(configuration.getImplicitlyWait()));
        }
        if (configuration.getScriptTimeout() != 0) {
            timeouts.scriptTimeout(ofMillis(configuration.getScriptTimeout()));
        }

        if (configuration.getWaitForElementTimeout() != 0
                && isNotBlank(configuration.getWaitForElementSelector())) {
            var elType = defaultIfNull(configuration.getWaitForElementType(),
                    WaitElementType.TAGNAME);
            LOG.debug("Waiting for element '{}' of type '{}' for '{}'.",
                    configuration.getWaitForElementSelector(), elType, url);

            var wait = new WebDriverWait(driver, Duration.ofMillis(
                    configuration.getWaitForElementTimeout()));
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    elType.getBy(configuration.getWaitForElementSelector())));

            LOG.debug("Done waiting for element '{}' of type '{}' for '{}'.",
                    configuration.getWaitForElementSelector(), elType, url);
        }

        if (StringUtils.isNotBlank(configuration.getLatePageScript())) {
            ((JavascriptExecutor) driver).executeScript(
                    configuration.getLatePageScript());
        }

        if (configuration.getThreadWait() != 0) {
            Sleeper.sleepMillis(configuration.getThreadWait());
        }

        var pageSource = driver.getPageSource();
        LOG.debug("Fetched page source length: {}", pageSource.length());
        return IOUtils.toInputStream(pageSource, StandardCharsets.UTF_8);
    }

    private HttpFetchResponse resolveDriverResponse(Doc doc) {
        HttpFetchResponse response = null;
        if (configuration.getHttpSniffer() != null) {
            var driverResponseFilter = configuration.getHttpSniffer().unbind();
            if (driverResponseFilter != null) {
                for (Entry<String, String> en
                        : driverResponseFilter.getHeaders()) {
                    var name = en.getKey();
                    var value = en.getValue();
                    // Content-Type + Content Encoding (Charset)
                    if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                        ApacheHttpUtil.applyContentTypeAndCharset(
                                value, doc.getDocRecord());
                    }
                    doc.getMetadata().add(name, value);
                }
                response = toFetchResponse(driverResponseFilter);
            }
        }

        //TODO we assume text/html as default until WebDriver expands its API
        // to obtain different types of files.
        if (doc.getDocRecord().getContentType() == null) {
            doc.getDocRecord().setContentType(ContentType.HTML);
        }

        return response;
    }

    private HttpFetchResponse toFetchResponse(
            DriverResponseFilter driverResponseFilter) {
        HttpFetchResponse response = null;
        if (driverResponseFilter != null) {
            //TODO validate status code
            var statusCode = driverResponseFilter.getStatusCode();
            var reason = driverResponseFilter.getReasonPhrase();

            var b = GenericHttpFetchResponse.builder()
                    .statusCode(statusCode)
                    .reasonPhrase(reason)
                    .userAgent(getUserAgent());
            if (statusCode >= 200 && statusCode < 300) {
                response = b.crawlDocState(CrawlDocState.NEW).build();
            } else {
                response = b.crawlDocState(CrawlDocState.BAD_STATUS).build();
            }
        }
        return response;
    }
//
//    @Override
//    protected void loadFetcherFromXML(XML xml) {
//        xml.populate(cfg);
//        xml.ifXML("screenshot", x -> {
//            var h =
//                    new ScreenshotHandler(streamFactory);
//            x.populate(h);
//            setScreenshotHandler(h);
//        });
//    }
//    @Override
//    protected void saveFetcherToXML(XML xml) {
//        configuration.saveToXML(xml);
//        if (screenshotHandler != null) {
//            screenshotHandler.saveToXML(xml.addElement("screenshot"));
//        }
//    }

}