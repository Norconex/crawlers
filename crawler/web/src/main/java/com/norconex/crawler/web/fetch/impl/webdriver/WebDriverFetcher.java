/* Copyright 2018-2025 Norconex Inc.
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
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.fetch.AbstractFetcher;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.FetchRequest;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.fetch.WebFetchRequest;
import com.norconex.crawler.web.fetch.WebFetchResponse;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpClientFetchResponse;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpClientFetcher;
import com.norconex.crawler.web.fetch.impl.webdriver.HttpSniffer.SniffedResponseHeaders;
import com.norconex.crawler.web.fetch.impl.webdriver.WebDriverFetcherConfig.WaitElementType;
import com.norconex.crawler.web.fetch.util.ApacheHttpUtil;

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
        extends AbstractFetcher<WebDriverFetcherConfig> {

    @Getter
    private final WebDriverFetcherConfig configuration =
            new WebDriverFetcherConfig();

    @JsonIgnore
    @Getter
    private String userAgent;

    private HttpSniffer httpSniffer;

    @JsonIgnore
    private WebDriverManager webDriverManager;

    @Override
    protected void fetcherStartup(CrawlContext c) {
        LOG.info("Starting WebDriver HTTP fetcher...");
        if (configuration.getHttpSniffer() != null) {
            httpSniffer = configuration.getHttpSniffer();
            httpSniffer.start();
            userAgent = httpSniffer.getConfiguration().getUserAgent();
        }
        webDriverManager = new WebDriverManager(configuration);
        if (StringUtils.isBlank(userAgent)) {
            userAgent = webDriverManager.safeCall(
                    driver -> (String) ((JavascriptExecutor) driver)
                            .executeScript("return navigator.userAgent;"));
        }
    }

    @Override
    protected void fetcherShutdown(CrawlContext c) {
        if (httpSniffer != null) {
            LOG.info("Shutting down {} HTTP sniffer...");
            Sleeper.sleepSeconds(5);
            httpSniffer.stop();
        }
    }

    @Override
    protected boolean acceptRequest(@NonNull FetchRequest req) {
        return HttpMethod.GET.is(((WebFetchRequest) req).getMethod());
    }

    @Override
    public WebFetchResponse fetch(FetchRequest req)
            throws FetchException {
        var method = ofNullable(
                ((WebFetchRequest) req).getMethod()).orElse(HttpMethod.GET);
        var doc = req.getDoc();
        if (method != HttpMethod.GET) {
            var reason = "HTTP " + method + " method not supported.";
            if (method == HttpMethod.HEAD) {
                reason += " To obtain headers, use GET with the HttpSniffer.";
            }
            return HttpClientFetchResponse.builder()
                    .resolutionStatus(CrawlDocStatus.UNSUPPORTED)
                    .reasonPhrase(reason)
                    .statusCode(-1)
                    .build();
        }

        LOG.debug("Fetching document: {}", doc.getReference());

        return webDriverManager.safeCall(driver -> {
            var fetchResponse = httpSniffer == null
                    ? withoutSniffer(driver, doc)
                    : withSniffer(driver, doc);
            if (configuration.getScreenshotHandler() != null) {
                configuration.getScreenshotHandler()
                        .takeScreenshot(driver, doc);
            }
            return fetchResponse;
        });
    }

    private WebFetchResponse withoutSniffer(WebDriver driver, CrawlDoc doc) {
        doc.setInputStream(fetchDocumentContent(driver, doc.getReference()));
        // We assume text/html until maybe WebDriver expands its
        // API to obtain different types of files.
        if (doc.getDocContext().getContentType() == null) {
            doc.getDocContext().setContentType(ContentType.HTML);
        }
        return HttpClientFetchResponse
                .builder()
                .resolutionStatus(CrawlDocStatus.NEW)
                .statusCode(200)
                .reasonPhrase("Real status code unknown. Use HTTP Sniffer "
                        + "to capture real status code.")
                .build();
    }

    private WebFetchResponse withSniffer(WebDriver driver, CrawlDoc doc) {
        var url = doc.getReference();
        var sniffer = configuration.getHttpSniffer();
        var crawlRequestId = UUID.randomUUID().toString();
        var augmentedUrl = new HttpURL(url);
        augmentedUrl.getQueryString().set(
                HttpSniffer.PARAM_REQUEST_ID, crawlRequestId);
        var respFuture = sniffer.track(crawlRequestId);
        var b = HttpClientFetchResponse.builder();

        // Do fetch
        doc.setInputStream(fetchDocumentContent(
                driver, augmentedUrl.toString()));

        var sniffedResp = getFutureSniffedResponse(
                respFuture, e -> buildFailedSniffedHttpFetchResponse(b, e));

        if (sniffedResp == null) {
            return b.build();
        }

        // Apply HTTP headers
        for (String name : sniffedResp.getHeaders().names()) {
            var values = sniffedResp.getHeaders().getAll(name);
            // Content-Type + Content Encoding (Charset)
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)
                    && !values.isEmpty()) {
                ApacheHttpUtil.applyContentTypeAndCharset(
                        values.get(0), doc.getDocContext());
            }
            doc.getMetadata().addList(name, values);
        }
        // Add collector metadata
        doc.getMetadata().add(
                WebDocMetadata.HTTP_STATUS_CODE,
                sniffedResp.getStatus().code());
        doc.getMetadata().add(
                WebDocMetadata.HTTP_STATUS_REASON,
                sniffedResp.getStatus().reasonPhrase());

        userAgent = StringUtils.firstNonBlank(
                userAgent, sniffedResp.getRequestUserAgent());

        WebFetchResponse fetchResponse = null;
        var status = sniffedResp.getStatus();
        b.statusCode(status.code())
                .reasonPhrase(status.reasonPhrase())
                .userAgent(userAgent);
        if (status.code() >= 200 && status.code() < 300) {
            fetchResponse = b.resolutionStatus(CrawlDocStatus.NEW).build();
        } else {
            fetchResponse = b.resolutionStatus(
                    CrawlDocStatus.BAD_STATUS).build();
        }
        return fetchResponse;
    }

    private void buildFailedSniffedHttpFetchResponse(
            HttpClientFetchResponse.HttpClientFetchResponseBuilder b,
            Exception e) {
        if (e != null) {
            b.exception(e);
            b.resolutionStatus(CrawlDocStatus.ERROR);
            b.userAgent(getUserAgent());
            if (e instanceof TimeoutException) {
                b.statusCode(HttpStatus.SC_GATEWAY_TIMEOUT);
                b.reasonPhrase("Sniffing timed out");
            } else {
                b.statusCode(HttpStatus.SC_BAD_GATEWAY);
                b.reasonPhrase("Sniffing failed");
            }
        }
    }

    private SniffedResponseHeaders getFutureSniffedResponse(
            CompletableFuture<SniffedResponseHeaders> future,
            Consumer<Exception> exceptionCatcher) {

        SniffedResponseHeaders resp = null;
        try {
            var timeout = ofNullable(configuration
                    .getHttpSniffer().getConfiguration()
                    .getResponseTimeout())
                            .orElse(HttpSnifferConfig.DEFAULT_RESPONSE_TIMEOUT);
            resp = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            exceptionCatcher.accept(e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                LOG.warn("Sniffer thread was interrupted while waiting "
                        + "for response.", e);
            } else if (e instanceof TimeoutException) {
                LOG.warn("Timed out waiting for sniffed response", e);
            } else if (e instanceof CancellationException) {
                LOG.warn("Sniffer async task was cancelled before completion",
                        e);
            } else if (e instanceof ExecutionException) {
                LOG.error("Error while executing sniffer async task",
                        e.getCause());
            } else {
                LOG.warn("Error while executing sniffer async task", e);
            }
        }
        return resp;
    }

    InputStream fetchDocumentContent(WebDriver driver, String url) {
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
                    elType.getBy(
                            configuration.getWaitForElementSelector())));
            LOG.debug(
                    "Done waiting for element '{}' of type '{}' for '{}'.",
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
        var len = pageSource == null ? "unknown" : pageSource.length();
        LOG.debug("Fetched page source length: {}", len);
        return IOUtils.toInputStream(pageSource, StandardCharsets.UTF_8);
    }
}
