/* Copyright 2018-2024 Norconex Inc.
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

import static java.util.Optional.ofNullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.fetch.AbstractFetcher;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpClientFetchResponse;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpClientFetcher;
import com.norconex.crawler.web.fetch.impl.webdriver.HttpSniffer.SniffedResponseHeader;
import com.norconex.crawler.web.fetch.impl.webdriver.WebDriverFetcherConfig.WaitElementType;
import com.norconex.crawler.web.fetch.util.ApacheHttpUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

//TODO lazy load driver like V3

/**
 * <p>
 * Uses Selenium WebDriver support for using native browsers to crawl documents.
 * Useful for crawling JavaScript-driven websites.
 * </p>
 *
 * <h2>Considerations</h2>
 * <p>
 * Relying on an external software to fetch pages can be slower and not as
 * scalable and may be less stable. Downloading of binaries and non-HTML file
 * format may not always be possible. The use of {@link HttpClientFetcher}
 * should be preferred whenever possible. Use at your own risk.
 * </p>
 * <p>
 * If it is important for each requests to be sharing the exact same browser
 * session, consider reducing the number of threads to <code>1</code> since
 * each thread has their own browser instance.
 * </p>
 * <p>
 * If you are using a Selenium grid and want to use multiple threads, make
 * sure you configure Selenium max number of sessions per node to align
 * with the number of threads you expect the crawler to use.
 * </p>
 *
 * <h2>Supported HTTP method</h2>
 * <p>
 * This fetcher only supports HTTP GET method.
 * </p>
 *
 * <h2>HTTP Headers</h2>
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
 * @since 3.0.0
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class WebDriverFetcher
        extends AbstractFetcher<
                HttpFetchRequest, HttpFetchResponse, WebDriverFetcherConfig>
        implements HttpFetcher {

    @Getter
    private final WebDriverFetcherConfig configuration =
            new WebDriverFetcherConfig();

    //--- Set on fetcher start-up ---
    @JsonIgnore
    private Browser browser;
    @JsonIgnore
    private CachedStreamFactory streamFactory;
    @JsonIgnore
    private String userAgent;
    @JsonIgnore
    private WebDriverLocation location;
    // Resolved capabilities of configured browser. Reused by all driver
    // instances created.
    @JsonIgnore
    private MutableCapabilities options;

    //--- Set on fetcher request ---

    // We need to make WebDrivers thread-safe
    private static final ThreadLocal<WebDriver> THREADED_DRIVER =
            new ThreadLocal<>();

    public String getUserAgent() {
        return userAgent;
    }

    @Override
    protected void fetcherStartup(CrawlerContext c) {
        LOG.info("Starting WebDriver HTTP fetcher...");
        browser = configuration.getBrowser();
        if (c != null) {
            //TODO add to base CrawlerContext class instead of casting?
            streamFactory = c.getStreamFactory();
        } else {
            streamFactory = new CachedStreamFactory();
        }

        location = new WebDriverLocation(
                configuration.getDriverPath(),
                configuration.getBrowserPath(),
                configuration.getRemoteURL());

        options = browser.createOptions(location);
        if (configuration.getHttpSniffer() != null) {
            LOG.info("Starting {} HTTP sniffer...", browser);
            configuration.getHttpSniffer().start(options);
            LOG.info("{} HTTP sniffer started", browser);
            userAgent = configuration
                    .getHttpSniffer()
                    .getConfiguration()
                    .getUserAgent();
        }
        options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
        configuration.getCapabilities().forEach((k, v) -> {
            options.setCapability(k, v);
        });
        // add arguments to drivers supporting it
        if (options instanceof FirefoxOptions fireFoxOptions) {
            fireFoxOptions.addArguments(configuration.getArguments());
        } else if (options instanceof EdgeOptions edgeOptions) {
            edgeOptions.addArguments(configuration.getArguments());
        }

        // We assign a driver here, for any case where the fetcher is used
        // in the main thread.
        fetcherThreadBegin(c);
    }

    @Override
    protected void fetcherThreadBegin(CrawlerContext context) {
        LOG.info("Creating {} web driver.", browser);
        THREADED_DRIVER.set(createWebDriver());
    }

    private WebDriver createWebDriver() {
        LOG.info("Creating {} web driver.", browser);
        WebDriver driver;
        if (configuration.isUseHtmlUnit()) {
            var v = browser.getHtmlUnitBrowser();
            if (v == null) {
                throw new CrawlerException(
                        "Unsupported HtmlUnit browser version: " + v);
            }
            driver = new HtmlUnitDriver(v, true);
        } else {
            driver = browser.createDriver(location, options);
        }
        if (StringUtils.isBlank(userAgent)) {
            userAgent = (String) ((JavascriptExecutor) driver).executeScript(
                    "return navigator.userAgent;");
        }
        return driver;
    }

    @Override
    protected boolean acceptRequest(@NonNull HttpFetchRequest req) {
        return HttpMethod.GET.is(req.getMethod());
    }

    @Override
    public HttpFetchResponse fetch(HttpFetchRequest req)
            throws FetchException {
        var method = ofNullable(req.getMethod()).orElse(HttpMethod.GET);
        var doc = req.getDoc();
        if (method != HttpMethod.GET) {
            var reason = "HTTP " + method + " method not supported.";
            if (method == HttpMethod.HEAD) {
                reason += " To obtain headers, use GET with a configured "
                        + "'httpSniffer'.";
            }
            return HttpClientFetchResponse.builder()
                    .resolutionStatus(DocResolutionStatus.UNSUPPORTED)
                    .reasonPhrase(reason)
                    .statusCode(-1)
                    .build();
        }

        LOG.debug("Fetching document: {}", doc.getReference());

        SniffedResponseHeader sniffedResponse = null;
        if (configuration.getHttpSniffer() != null) {
            sniffedResponse = configuration
                    .getHttpSniffer()
                    .track(doc.getReference());
        }

        doc.setInputStream(fetchDocumentContent(doc.getReference()));

        var fetchResponse = resolveDriverResponse(doc, sniffedResponse);

        if (configuration.getHttpSniffer() != null) {
            configuration.getHttpSniffer().untrack(doc.getReference());
        }

        if (configuration.getScreenshotHandler() != null) {
            configuration.getScreenshotHandler().takeScreenshot(
                    THREADED_DRIVER.get(), doc);
        }

        if (fetchResponse != null) {
            return fetchResponse;
        }
        return HttpClientFetchResponse
                .builder()
                .resolutionStatus(DocResolutionStatus.NEW)
                .statusCode(200)
                .reasonPhrase("No exception thrown, but real status code "
                        + "unknown. Capture headers for real status code.")
                .userAgent(getUserAgent())
                .build();
    }

    @Override
    protected void fetcherThreadEnd(CrawlerContext crawler) {
        LOG.info("Shutting down {} web driver.", browser);
        var driver = THREADED_DRIVER.get();
        if (driver != null) {
            driver.quit();
        }
        THREADED_DRIVER.remove();
    }

    @Override
    protected void fetcherShutdown(CrawlerContext c) {
        // We close a driver here, for any case where the fetcher is used
        // in the main thread.
        fetcherThreadEnd(c);

        if (configuration.getHttpSniffer() != null) {
            LOG.info("Shutting down {} HTTP sniffer...", browser);
            Sleeper.sleepSeconds(5);
            configuration.getHttpSniffer().stop();
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
        if (configuration.getPageLoadTimeout() != null) {
            timeouts.pageLoadTimeout(configuration.getPageLoadTimeout());
        }
        if (configuration.getImplicitlyWait() != null) {
            timeouts.implicitlyWait(configuration.getImplicitlyWait());
        }
        if (configuration.getScriptTimeout() != null) {
            timeouts.scriptTimeout(configuration.getScriptTimeout());
        }

        if (configuration.getWaitForElementTimeout() != null
                && StringUtils.isNotBlank(
                        configuration.getWaitForElementSelector())) {
            var elType = ObjectUtils.defaultIfNull(
                    configuration.getWaitForElementType(),
                    WaitElementType.TAGNAME);
            LOG.debug("Waiting for element '{}' of type '{}' for '{}'.",
                    configuration.getWaitForElementSelector(), elType, url);

            var wait = new WebDriverWait(
                    driver, configuration.getWaitForElementTimeout());
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    elType.getBy(configuration.getWaitForElementSelector())));
            LOG.debug("Done waiting for element '{}' of type '{}' for '{}'.",
                    configuration.getWaitForElementSelector(), elType, url);
        }

        if (StringUtils.isNotBlank(configuration.getLatePageScript())) {
            ((JavascriptExecutor) driver).executeScript(
                    configuration.getLatePageScript());
        }

        if (configuration.getThreadWait() != null) {
            Sleeper.sleepMillis(configuration.getThreadWait().toMillis());
        }

        var pageSource = driver.getPageSource();

        LOG.debug("Fetched page source length: {}", pageSource.length());
        return IOUtils.toInputStream(pageSource, StandardCharsets.UTF_8);
    }

    private HttpFetchResponse resolveDriverResponse(
            CrawlDoc doc, SniffedResponseHeader sniffedResponse) {

        HttpFetchResponse response = null;
        if (sniffedResponse != null) {
            sniffedResponse.getHeaders().asMap().forEach((k, v) -> {
                var values = (List<String>) v;
                // Content-Type + Content Encoding (Charset)
                if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(k)
                        && !values.isEmpty()) {
                    ApacheHttpUtil.applyContentTypeAndCharset(
                            values.get(0), doc.getDocContext());
                }
                doc.getMetadata().addList(k, values);
            });

            var statusCode = sniffedResponse.getStatusCode();
            var b = HttpClientFetchResponse
                    .builder()
                    .statusCode(statusCode)
                    .reasonPhrase(sniffedResponse.getReasonPhrase())
                    .userAgent(getUserAgent());
            if (statusCode >= 200 && statusCode < 300) {
                response = b.resolutionStatus(DocResolutionStatus.NEW).build();
            } else {
                response = b.resolutionStatus(
                        DocResolutionStatus.BAD_STATUS).build();
            }
        }

        //TODO we assume text/html as default until WebDriver expands its API
        // to obtain different types of files.
        if (doc.getDocContext().getContentType() == null) {
            doc.getDocContext().setContentType(ContentType.HTML);
        }
        return response;
    }
}
