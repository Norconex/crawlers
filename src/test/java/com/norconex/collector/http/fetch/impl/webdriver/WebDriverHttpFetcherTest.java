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

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.http.TestUtil;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.fetch.HttpFetchException;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.server.TestServer;
import com.norconex.collector.http.server.TestServerBuilder;
import com.norconex.commons.lang.OSResource;
import com.norconex.commons.lang.file.WebFile;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.importer.doc.Doc;

@Disabled
public class WebDriverHttpFetcherTest  {

    private static final Logger LOG =
            LoggerFactory.getLogger(WebDriverHttpFetcherTest.class);

    // https://googlechromelabs.github.io/chrome-for-testing/
    private static final Path chromeDriverPath = new OSResource<Path>()
            .win(WebFile.create("https://storage.googleapis.com/"
                    + "chrome-for-testing-public/135.0.7049.95/win64/"
                    + "chromedriver-win64.zip!/chromedriver-win64/"
                    + "chromedriver.exe",
                    "chromedriver-135.0.7049.95.exe"))
            .get();

    // https://github.com/mozilla/geckodriver/releases/
    private static final Path firefoxDriverPath = new OSResource<Path>()
            .win(WebFile.create(
                    "https://github.com/mozilla/geckodriver/releases/download/"
                  + "v0.34.0/geckodriver-v0.34.0-win64.zip!/geckodriver.exe",
                    "geckodriver-0.34.0.exe"))
            .get();


    // https://github.com/operasoftware/operachromiumdriver/releases
    private static final Path operaDriverPath = new OSResource<Path>()
            .win(WebFile.create("https://github.com/operasoftware/"
                    + "operachromiumdriver/releases/download/v.121.0.6167.140/"
                    + "operadriver_win64.zip!/operadriver_win64/"
                    + "operadriver.exe",
                    "operadriver-121.0.6167.140.exe"))
            .get();

    static Stream<WebDriverHttpFetcher> browsersProvider() {
        return Stream.of(
                createFetcher(Browser.FIREFOX, firefoxDriverPath),
                createFetcher(Browser.CHROME, chromeDriverPath),
                createFetcher(Browser.OPERA, operaDriverPath)
        );
    }

    private static final int LARGE_CONTENT_MIN_SIZE = 5 * 1024 *1024;

    private static TestServer server = new TestServerBuilder()
            .addPackage("server/js-rendered")
            .setEnableHttps(true)
            .addServlet(new HttpServlet() {
                private static final long serialVersionUID = 1L;
                @Override
                protected void doGet(
                        HttpServletRequest req, HttpServletResponse resp)
                        throws ServletException, IOException {
                    resp.addHeader("TEST_KEY", "test_value");
                    resp.getWriter().write("HTTP headers test. "
                            + "TEST_KEY should be found in HTTP headers");
                    resp.flushBuffer();
                }
            }, "/headers")
            .addServlet(new HttpServlet() {
                private static final long serialVersionUID = 1L;
                @Override
                protected void doGet(
                        HttpServletRequest req, HttpServletResponse resp)
                        throws ServletException, IOException {
                    // Return more than 2 MB of text.
                    var content = RandomStringUtils.randomAlphanumeric(
                            LARGE_CONTENT_MIN_SIZE);
                    resp.getWriter().write(content);
                    resp.setHeader(HttpHeader.CONTENT_LENGTH.toString(),
                            Long.toString(content.getBytes().length));
                    resp.flushBuffer();
                }
            }, "/largeContent")
            .build();

    @BeforeAll
    static void beforeClass() {
        server.start();
    }
    @AfterAll
    static void afterClass() {
        server.stop();
    }

    @BrowserTest
    public void testFetchingJsGeneratedContent(
            WebDriverHttpFetcher fetcher) throws Exception {
        assumeDriverPresent(fetcher);
        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            var doc = fetch(fetcher, "/");
            LOG.debug("'/' META: {}", doc.getMetadata());
            Assertions.assertTrue(IOUtils.toString(
                    doc.getInputStream(), StandardCharsets.UTF_8).contains(
                            "JavaScript-rendered!"));
        });
    }

    // Remove "@Disabled" to manually test that screenshots are generated
    @Disabled
    @BrowserTest
    public void testTakeScreenshots(
            WebDriverHttpFetcher fetcher) throws Exception {
        assumeDriverPresent(fetcher);
        var h = new ScreenshotHandler();
        h.setTargetDir(Paths.get("./target/screenshots"));
        h.setCssSelector("#applePicture");
        fetcher.setScreenshotHandler(h);

        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            fetch(fetcher, "/apple.html");
        });
    }

    @BrowserTest
    public void testFetchingHeadersUsingSniffer(
            WebDriverHttpFetcher fetcher) throws Exception {
        assumeDriverPresent(fetcher);

        // Test picking up headers
        Assumptions.assumeTrue(
                isProxySupported(fetcher.getConfig().getBrowser()),
                "SKIPPING: " + fetcher.getConfig().getBrowser().name()
                + " does not support setting proxy to obtain headers.");

        var cfg = new HttpSnifferConfig();
        fetcher.getConfig().setHttpSnifferConfig(cfg);

        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            var doc = fetch(fetcher, "/headers");
            LOG.debug("'/headers' META: {}", doc.getMetadata());
            Assertions.assertEquals(
                    "test_value", doc.getMetadata().getString("TEST_KEY"));

            Assertions.assertEquals("OK", doc.getMetadata().getString(
                    HttpDocMetadata.HTTP_STATUS_REASON));
            Assertions.assertEquals(200, doc.getMetadata().getInteger(
                    HttpDocMetadata.HTTP_STATUS_CODE, -1));
        });
    }

    @BrowserTest
    public void testPageScript(
            WebDriverHttpFetcher fetcher) throws Exception {
        assumeDriverPresent(fetcher);
        fetcher.getConfig().setLatePageScript(
                "document.getElementsByTagName('h1')[0].innerHTML='Melon';");

        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            var doc = fetch(fetcher, "/orange.html");
            var h1 = IOUtils.toString(doc.getInputStream(),
                    StandardCharsets.UTF_8).replaceFirst(
                            "(?s).*<h1>(.*?)</h1>.*", "$1");
            LOG.debug("New H1: {}", h1);
            Assertions.assertEquals("Melon", h1);
        });
    }

    @BrowserTest
    public void testResolvingUserAgent(WebDriverHttpFetcher fetcher)
            throws Exception {
        assumeDriverPresent(fetcher);
        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            fetch(fetcher, "/headers.html");
            var userAgent = fetcher.getUserAgent();
            LOG.debug("User agent: {}", userAgent);
            Assertions.assertTrue(
                    StringUtils.isNotBlank(userAgent),
                    "Could not resolve user agent.");
        });
    }

    // Test case for https://github.com/Norconex/collector-http/issues/751
    // Should support large content when increasing buffer size.
    @BrowserTest
    public void testLargeContentUsingSniffer(WebDriverHttpFetcher fetcher)
            throws Exception {
        assumeDriverPresent(fetcher);

        // Test picking up headers
        Assumptions.assumeTrue(
                isProxySupported(fetcher.getConfig().getBrowser()),
                "SKIPPING: " + fetcher.getConfig().getBrowser().name()
                + " does not support setting proxy.");

        var cfg = new HttpSnifferConfig();
        cfg.setMaxBufferSize(6 * 1024 * 1024);
        cfg.setResponseTimeout(Duration.ofSeconds(10));
        fetcher.getConfig().setHttpSnifferConfig(cfg);

        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            var doc = fetch(fetcher, "/largeContent");
            var txt = IOUtils.toString(
                    doc.getInputStream(), StandardCharsets.UTF_8);
            LOG.debug("Large content extracted byte length: {}",
                    txt.getBytes(StandardCharsets.UTF_8).length);
            Assertions.assertTrue(txt.length() >= LARGE_CONTENT_MIN_SIZE);
        });
    }

    private static WebDriverHttpFetcher createFetcher(
            Browser browser, Path driverPath) {
        var fetcher = new WebDriverHttpFetcher();
        fetcher.getConfig().setBrowser(browser);
        fetcher.getConfig().setDriverPath(driverPath);
        fetcher.getConfig().setPageLoadTimeout(5 * 1000);
        fetcher.getConfig().setScriptTimeout(5 * 1000);
        fetcher.getConfig().setThreadWait(5 * 1000);
        fetcher.getConfig().setImplicitlyWait(5 * 1000);
        fetcher.getConfig().setWaitForElementTimeout(5 * 1000);
        return fetcher;
    }
    private static boolean isDriverPresent(Path driverPath) {
        try {
            return driverPath != null && driverPath.toFile().exists();
        } catch (Exception e) {
            LOG.debug("Could not verify driver presence at: {}. Error: {}",
                    driverPath, e.getMessage());
            return false;
        }
    }

    private Doc fetch(WebDriverHttpFetcher fetcher, String urlPath)
            throws HttpFetchException {
        var doc = new CrawlDoc(new HttpDocInfo(
                "http://localhost:" + server.getPort() + urlPath),
                new CachedStreamFactory(10000, 10000).newInputStream());
        fetcher.fetch(doc, HttpMethod.GET);
        return doc;
    }


    // Returns false for browsers not supporting setting proxies, which
    // is required to capture headers.
    private boolean isProxySupported(Browser browser) {
        // Edge no proxy support:
        //     https://docs.microsoft.com/en-us/openspecs/ie_standards/
        //     ms-webdriver/4d9e3215-1111-1111-a0bf-5b013a3267fd
        return browser != Browser.EDGE;
    }

    private void assumeDriverPresent(WebDriverHttpFetcher fetcher) {
        Assumptions.assumeTrue(fetcher != null);
        Assumptions.assumeTrue(
                isDriverPresent(fetcher.getConfig().getDriverPath()),
                "SKIPPING: No driver found for "
                        + fetcher.getConfig().getBrowser().name());
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest(name = "browser: {0}")
    @MethodSource("browsersProvider")
    @interface BrowserTest {
    }
}
