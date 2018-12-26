/* Copyright 2018 Norconex Inc.
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
package com.norconex.collector.http.fetch.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.server.TestServer;
import com.norconex.collector.http.server.TestServerBuilder;
import com.norconex.commons.lang.OSResource;
import com.norconex.commons.lang.file.WebFile;
import com.norconex.commons.lang.io.CachedStreamFactory;

//TODO merge http client with document fetcher.
// have 1 doc fetcher and 1 http fetcher that can be the same or different.
// have ability to specify different fetchers for different URL patterns.
//@Ignore

@RunWith(value = Parameterized.class)
public class WebDriverHttpFetcherTest  {

    private static final Logger LOG =
            LoggerFactory.getLogger(WebDriverHttpFetcherTest.class);

    private static TestServer server = new TestServerBuilder()
            .addPackage("server/js-rendered")
            .addServlet(new HttpServlet() {
                private static final long serialVersionUID = 1L;
                @Override
                protected void doGet(HttpServletRequest req,
                        HttpServletResponse resp)
                        throws ServletException, IOException {
                    resp.addHeader("TEST_KEY", "test_value");
                    resp.getWriter().write("HTTP headers test. "
                            + "TEST_KEY should be found in HTTP headers");
                    super.doGet(req, resp);

                }
            }, "/headers")
            .build();

//  https://chromedriver.storage.googleapis.com/2.43/chromedriver_linux64.zip
//  https://chromedriver.storage.googleapis.com/2.43/chromedriver_mac64.zip
    private static final Path chromeDriverPath = new OSResource<Path>()
            .win(WebFile.create("https://chromedriver.storage.googleapis.com/"
                    + "2.43/chromedriver_win32.zip!/chromedriver.exe",
                    "chromedriver-2.43.exe"))
            .get();

//  https://github.com/mozilla/geckodriver/releases/download/v0.23.0/geckodriver-v0.23.0-win64.zip
//  https://developer.mozilla.org/en-US/docs/Web/WebDriver
//  https://ftp.mozilla.org/pub/firefox/releases/55.0.3/
    private static final Path firefoxDriverPath = new OSResource<Path>()
            .win(WebFile.create(
                    "https://github.com/mozilla/geckodriver/releases/download/"
                  + "v0.23.0/geckodriver-v0.23.0-win64.zip!/geckodriver.exe",
                    "geckodriver-0.23.exe"))
            .get();

    private static final Path edgeDriverPath = new OSResource<Path>()
            .win(WebFile.create("https://download.microsoft.com/download/F/8/A/"
                    + "F8AF50AB-3C3A-4BC4-8773-DC27B32988DD/"
                    + "MicrosoftWebDriver.exe",
                    "edgedriver-6.17134.exe"))
            .get();

    private final WebDriverBrowser browser;
    private final Path driverPath;


    public WebDriverHttpFetcherTest(WebDriverBrowser browser, Path driverPath) {
        super();
        this.browser = browser;
        this.driverPath = driverPath;
    }

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> browsers() {
        return Arrays.asList(new Object[][]{
                {WebDriverBrowser.FIREFOX, firefoxDriverPath},
                {WebDriverBrowser.CHROME, chromeDriverPath},
                {WebDriverBrowser.EDGE, edgeDriverPath},
        });
    }

    @BeforeClass
    public static void beforeClass() throws IOException {
        server.start();
    }
    @AfterClass
    public static void afterClass() throws IOException {
        server.stop();
    }
    @Before
    public void before() throws IOException {
        Assume.assumeTrue("SKIPPING: No driver for " + browser.name(),
                isDriverPresent(driverPath));
    }


    @Test
    public void testWebDriverFetcher() throws IOException {

        // Optional, if not specified, WebDriver will search your
        // path for chromedriver.
        WebDriverHttpFetcher fetcher = new WebDriverHttpFetcher();
        fetcher.setBrowser(browser);
        fetcher.setDriverPath(driverPath);
        fetcher.setDriverProxyDisabled(false);

        try {
            // simulate crawler startup
            fetcher.crawlerStartup(null);

            HttpDocument doc = null;

            // Test picking up javascript-generated content
            doc = fetch(fetcher, "/");
            LOG.debug("'/' META: " + doc.getMetadata());
            Assert.assertThat(IOUtils.toString(
                    doc.getInputStream(), StandardCharsets.UTF_8),
                    CoreMatchers.containsString("JavaScript-rendered!"));
        } finally {
            fetcher.crawlerShutdown(null);
        }
    }

    @Test
    public void testFetchHeaders() throws IOException {

        // Test picking up headers
        Assume.assumeTrue("SKIPPING: " + browser.name()
                + " does not support setting proxy to obtain headers.",
                isProxySupported(browser));

        WebDriverHttpFetcher fetcher = new WebDriverHttpFetcher();
        fetcher.setBrowser(browser);
        fetcher.setDriverPath(driverPath);
        fetcher.setDriverProxyDisabled(false);

        try {
            // simulate crawler startup
            fetcher.crawlerStartup(null);
            HttpDocument doc = fetch(fetcher, "/headers");
            LOG.debug("'/headers' META: " + doc.getMetadata());
            Assert.assertEquals(
                    "test_value", doc.getMetadata().getString("TEST_KEY"));
        } finally {
            fetcher.crawlerShutdown(null);
        }
    }


    private HttpDocument fetch(WebDriverHttpFetcher fetcher, String urlPath) {
        HttpDocument doc = new HttpDocument(
                "http://127.0.0.1:" + server.getPort() + urlPath,
                new CachedStreamFactory(10000, 10000).newInputStream());
        /*HttpFetchResponse response = */ fetcher.fetchDocument(doc);
        return doc;
    }

    private boolean isDriverPresent(Path driverPath) {
        try {
            return driverPath != null && driverPath.toFile().exists();
        } catch (Exception e) {
            LOG.debug("Could not verify driver presence at: {}. Error: {}",
                    driverPath, e.getMessage());
            return false;
        }
    }
    // Returns false for browsers not supporting setting proxies, which
    // is required to capture headers.
    private boolean isProxySupported(WebDriverBrowser browser) {
        return browser != WebDriverBrowser.EDGE;
    }



//    @Test
//    public void testChromeDriver() throws IOException {
//        Assume.assumeNotNull(driverPath.getResource());
//
//
//        // Optional, if not specified, WebDriver will search your path for chromedriver.
//        System.setProperty(
//                "webdriver.chrome.driver", driverPath.getResource().toString());
//        ChromeOptions options = new ChromeOptions();
//        options.setHeadless(true);
//        WebDriver driver = new ChromeDriver(options);
//        driver.get("http://127.0.0.1:" + server.getPort() + "/");
////        driver.get("http://www.google.com?q=test");
////        WebElement searchBox = driver.findElement(By.name("q"));
////        searchBox.sendKeys("ChromeDriver");
////        searchBox.submit();
////        Thread.sleep(5000);  // Let the user actually see something!
//
//
//        Assert.assertThat(driver.getPageSource(),
//                CoreMatchers.containsString("JavaScript-rendered!"));
//
////        System.out.println("SOURCE:\n" + driver.getPageSource());
//
//        driver.quit();
//
//    }

//    @Test
//    public void testGetPDF() throws IOException {
//        Assume.assumeNotNull(chromeDriverPath);
//
//        // Optional, if not specified, WebDriver will search your path for chromedriver.
//        System.setProperty(
//                "webdriver.chrome.driver", chromeDriverPath.toString());
//        ChromeOptions options = new ChromeOptions();
//        options.setHeadless(true);
//        options.setCapability("pdfjs.disabled", true);
//        WebDriver driver = new ChromeDriver(options);
//        driver.get("http://127.0.0.1:" + server.getPort() + "/tiny.pdf");
////        driver.get("http://www.google.com?q=test");
//        Sleeper.sleepSeconds(5); // Let the user actually see something!
////        WebElement searchBox = driver.findElement(By.name("q"));
////        searchBox.sendKeys("ChromeDriver");
////        searchBox.submit();
////        Thread.sleep(5000);  // Let the user actually see something!
//
//
////        Assert.assertThat(driver.getPageSource(),
////                CoreMatchers.containsString("JavaScript-rendered!"));
//
//        System.out.println("SOURCE:\n" + driver.getPageSource());
//
//        driver.quit();
//
//    }
}
