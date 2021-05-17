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

import java.io.File;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
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
import com.norconex.collector.http.fetch.HttpFetchException;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.server.TestServer;
import com.norconex.collector.http.server.TestServerBuilder;
import com.norconex.commons.lang.OSResource;
import com.norconex.commons.lang.file.WebFile;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.importer.doc.Doc;

//TODO if EDGE fails, log an error and Assume false (ignore the test).

//TODO merge http client with document fetcher.
// have 1 doc fetcher and 1 http fetcher that can be the same or different.
// have ability to specify different fetchers for different URL patterns.
//@Disabled
public class WebDriverHttpFetcherTest  {

    private static final Logger LOG =
            LoggerFactory.getLogger(WebDriverHttpFetcherTest.class);

//  https://sites.google.com/a/chromium.org/chromedriver/downloads
    private static final Path chromeDriverPath = new OSResource<Path>()
            .win(WebFile.create("https://chromedriver.storage.googleapis.com/"
                    + "90.0.4430.24/chromedriver_win32.zip!/chromedriver.exe",
                    "chromedriver-90.0.4430.24.exe"))
            .get();

//  https://github.com/mozilla/geckodriver/releases/
    private static final Path firefoxDriverPath = new OSResource<Path>()
            .win(WebFile.create(
                    "https://github.com/mozilla/geckodriver/releases/download/"
                  + "v0.29.1/geckodriver-v0.29.1-win64.zip!/geckodriver.exe",
                    "geckodriver-0.29.1.exe"))
            .get();

//  https://developer.microsoft.com/en-us/microsoft-edge/tools/webdriver/
//    private static final Path edgeDriverPath = new OSResource<Path>()
//            .win(WebFile.create("https://msedgedriver.azureedge.net/85.0.564.51"
//                    + "/edgedriver_win64.zip!/msedgedriver.exe",
//                    "edgedriver-85.0.564.51.exe"))
//            .get();

//  https://github.com/operasoftware/operachromiumdriver/releases
    private static final Path operaDriverPath = new OSResource<Path>()
            .win(WebFile.create("https://github.com/operasoftware/"
                    + "operachromiumdriver/releases/download/v.89.0.4389.82/"
                    + "operadriver_win64.zip!/operadriver_win64/"
                    + "operadriver.exe",
                    "operadriver-89.0.4389.82.exe"))
            .get();

    static Stream<WebDriverHttpFetcher> browsersProvider() {
        return Stream.of(
                createFetcher(Browser.FIREFOX, firefoxDriverPath),
                createFetcher(Browser.CHROME, chromeDriverPath),
//                createFetcher(Browser.EDGE, edgeDriverPath),
                createFetcher(Browser.OPERA, operaDriverPath)
        );
    }

    private static final int LARGE_CONTENT_MIN_SIZE = 5 * 1024 *1024;

    private static TestServer server = new TestServerBuilder()
            .addPackage("server/js-rendered")
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
                    resp.getWriter().write(RandomStringUtils.randomAlphanumeric(
                            LARGE_CONTENT_MIN_SIZE));
                    resp.flushBuffer();
                }
            }, "/largeContent")
            .build();

    @BeforeAll
    public static void beforeClass() {
        server.start();
    }
    @AfterAll
    public static void afterClass() {
        server.stop();
    }

    @BrowserTest
    public void testFetchingJsGeneratedContent(
            WebDriverHttpFetcher fetcher) throws Exception {
        assumeDriverPresent(fetcher);
        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            Doc doc = fetch(fetcher, "/");
            LOG.debug("'/' META: " + doc.getMetadata());
            Assertions.assertTrue(IOUtils.toString(
                    doc.getInputStream(), StandardCharsets.UTF_8).contains(
                            "JavaScript-rendered!"));
        });
    }

    // Remove ignore to manually test that screenshots are generated
    @Disabled
    @BrowserTest
    public void testTakeScreenshots(
            WebDriverHttpFetcher fetcher) throws Exception {
        assumeDriverPresent(fetcher);
        ScreenshotHandler h = new ScreenshotHandler();
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

        HttpSnifferConfig cfg = new HttpSnifferConfig();
        fetcher.getConfig().setHttpSnifferConfig(cfg);

        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            Doc doc = fetch(fetcher, "/headers");
            LOG.debug("'/headers' META: " + doc.getMetadata());
            Assertions.assertEquals(
                    "test_value", doc.getMetadata().getString("TEST_KEY"));
        });
    }

    @BrowserTest
    public void testPageScript(
            WebDriverHttpFetcher fetcher) throws Exception {
        assumeDriverPresent(fetcher);
        fetcher.getConfig().setLatePageScript(
                "document.getElementsByTagName('h1')[0].innerHTML='Melon';");

        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            Doc doc = fetch(fetcher, "/orange.html");
            String h1 = IOUtils.toString(doc.getInputStream(),
                    StandardCharsets.UTF_8).replaceFirst(
                            "(?s).*<h1>(.*?)</h1>.*", "$1");
            LOG.debug("New H1: " + h1);
            Assertions.assertEquals("Melon", h1);
        });
    }

    @BrowserTest
    public void testResolvingUserAgent(WebDriverHttpFetcher fetcher)
            throws Exception {
        assumeDriverPresent(fetcher);
        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            String userAgent = fetcher.getUserAgent();
            LOG.debug("User agent: {}", userAgent);
            Assertions.assertTrue(
                    StringUtils.isNotBlank(userAgent),
                    "Could not resolve user agent.");
        });
    }

    // Test case for https://github.com/Norconex/collector-http/issues/751
    @BrowserTest
    public void testLargeContentUsingSniffer(WebDriverHttpFetcher fetcher)
            throws Exception {
        assumeDriverPresent(fetcher);

        // Test picking up headers
        Assumptions.assumeTrue(
                isProxySupported(fetcher.getConfig().getBrowser()),
                "SKIPPING: " + fetcher.getConfig().getBrowser().name()
                + " does not support setting proxy.");

        HttpSnifferConfig cfg = new HttpSnifferConfig();
        cfg.setMaxBufferSize(6 * 1024 * 1024);
        fetcher.getConfig().setHttpSnifferConfig(cfg);

        TestUtil.mockCrawlerRunLifeCycle(fetcher, () -> {
            Doc doc = fetch(fetcher, "/largeContent");
FileUtils.copyInputStreamToFile(doc.getInputStream(), new File("C:\\temp\\AAAAA.txt"));
            String txt = IOUtils.toString(
                    doc.getInputStream(), StandardCharsets.UTF_8);
            LOG.error("Large content extracted byte length: {}",
                    txt.getBytes(StandardCharsets.UTF_8).length);
            Assertions.assertTrue(txt.length() >= LARGE_CONTENT_MIN_SIZE);
        });
    }

    private static WebDriverHttpFetcher createFetcher(
            Browser browser, Path driverPath) {
        WebDriverHttpFetcher fetcher = new WebDriverHttpFetcher();
        fetcher.getConfig().setBrowser(browser);
        fetcher.getConfig().setDriverPath(driverPath);
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
        CrawlDoc doc = new CrawlDoc(new HttpDocInfo(
                "http://localhost:" + server.getPort() + urlPath),
                new CachedStreamFactory(10000, 10000).newInputStream());
        /*IHttpFetchResponse response = */ fetcher.fetch(doc, HttpMethod.GET);
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
