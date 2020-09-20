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
import java.util.logging.Level;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.HttpHeaders;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriver.Timeouts;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.support.ThreadGuard;
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
import com.norconex.collector.http.fetch.util.ApacheHttpUtil;
import com.norconex.commons.lang.SLF4JUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Uses Selenium WebDriver support for using native browsers to crawl documents.
 * Useful for crawling JavaScript-driven websites.  To prevent launching a new
 * browser at each requests and to help maintain web sessions a browser
 * instance is started as a service for the life-duration of the crawler.
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
 *   <browserPath>(browser executable or blank to detect)</browserPath>
 *   <driverPath>(driver executable or blank to detect)</driverPath>
 *
 *   <!-- Optional browser capabilities supported by the web driver. -->
 *   <capabilities>
 *     <capability name="(capability name)">(capability value)</capability>
 *     <!-- multiple "capability" tags allowed -->
 *   </capabilities>
 *
 *   <!-- Optionally take screenshots of each web pages. -->
 *   <screenshot>
 *
 *
 *
 *
 *
 *
 *                    TODO DOCUMENT PROPERLY
 *
 *
 *
 *   </screenshot>
 *
 *   <initScript>
 *     (Optional JavaScript code to be run during this class initialization.)
 *   </initScript>
 *   <pageScript>
 *     (Optional JavaScript code to be run after each pages are loaded.)
 *   </pageScript>
 *
 *   <!-- Timeouts, in milliseconds, or human-readable format (English).
 *      - Default is zero (not set).
 *      -->
 *   <pageLoadTimeout>
 *     (Max wait time for a page to load before throwing an error.)
 *   </pageLoadTimeout>
 *   <implicitlyWait>
 *     (Wait for that long for the page to finish rendering.)
 *   </implicitlyWait>
 *   <scriptTimeout>
 *     (Max wait time for a scripts to execute before throwing an error.)
 *   </scriptTimeout>
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
 *     <port>(default is 0 = random free port)"</port>
 *     <userAgent>(optionally overwrite browser user agent)</userAgent>
 *
 *     <!-- Optional HTTP request headers passed on every HTTP requests -->
 *     <headers>
 *       <header name="(header name)">(header value)</header>
 *       <!-- You can repeat this header tag as needed. -->
 *     </headers>
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
    private MutableCapabilities options;
    private final ThreadLocal<WebDriver> driverTL = new ThreadLocal<>();

    public WebDriverHttpFetcher() {
        this(new WebDriverHttpFetcherConfig());
    }
    public WebDriverHttpFetcher(WebDriverHttpFetcherConfig config) {
        super();
        Objects.requireNonNull(config, "'config' must not be null.");
        this.cfg = config;
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
        LOG.info("Starting {} driver service...", cfg.getBrowser());

        if (c != null) {
            streamFactory = c.getStreamFactory();
        } else {
            streamFactory = new CachedStreamFactory();
        }

        options = cfg.getBrowser().capabilities(cfg.getBrowserPath());
        configureWebDriverLogging(options);

        options.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
        options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);

        options.merge(cfg.getCapabilities());

        if (cfg.getHttpSnifferConfig() != null) {
            httpSniffer = new HttpSniffer();
            httpSniffer.start(options, cfg.getHttpSnifferConfig());
            userAgent = cfg.getHttpSnifferConfig().getUserAgent();
        }
    }

    @Override
    protected void fetcherThreadBegin(HttpCrawler crawler) {
        LOG.info("Creating {} remote driver.", cfg.getBrowser());
        driverTL.set(createWebDriver());
    }
    @Override
    protected void fetcherThreadEnd(HttpCrawler crawler) {
        LOG.info("Shutting down {} remote driver.", cfg.getBrowser());
        if (driverTL.get() != null) {
            driverTL.get().quit();
            driverTL.remove();
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

    private WebDriver createWebDriver() {
        WebDriver driver =
                cfg.getBrowser().driver(cfg.getDriverPath(), options);
        driverTL.set(driver);

        if (cfg.getWindowSize() != null) {
            driver.manage().window().setSize(
                    new org.openqa.selenium.Dimension(
                            cfg.getWindowSize().width,
                            cfg.getWindowSize().height));
        }

        Timeouts timeouts = driver.manage().timeouts();
        if (cfg.getPageLoadTimeout() != 0) {
            timeouts.pageLoadTimeout(cfg.getPageLoadTimeout(), MILLISECONDS);
        }
        if (cfg.getImplicitlyWait() != 0) {
            timeouts.implicitlyWait(cfg.getImplicitlyWait(), MILLISECONDS);
        }
        if (cfg.getScriptTimeout() != 0) {
            timeouts.setScriptTimeout(cfg.getScriptTimeout(), MILLISECONDS);
        }
        if (StringUtils.isBlank(userAgent)) {
            userAgent = (String) ((JavascriptExecutor) driver).executeScript(
                    "return navigator.userAgent;");
        }
        if (StringUtils.isNotBlank(cfg.getInitScript())) {
            ((JavascriptExecutor) driver).executeScript(cfg.getInitScript());
        }
        return ThreadGuard.protect(driver);
    }

    private void configureWebDriverLogging(MutableCapabilities capabilities) {
        LoggingPreferences logPrefs = new LoggingPreferences();
        Level level = SLF4JUtil.toJavaLevel(SLF4JUtil.getLevel(LOG));
        logPrefs.enable(LogType.PERFORMANCE, level);
        logPrefs.enable(LogType.PROFILER, level);
        logPrefs.enable(LogType.BROWSER, level);
        logPrefs.enable(LogType.CLIENT, level);
        logPrefs.enable(LogType.DRIVER, level);
        logPrefs.enable(LogType.SERVER, level);
        capabilities.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
    }

    @Override
    public IHttpFetchResponse fetch(CrawlDoc doc, HttpMethod httpMethod)
            throws HttpFetchException {

        HttpMethod method = ofNullable(httpMethod).orElse(GET);
        if (method != GET) {
            String reason = "HTTP " + httpMethod + " method not supported.";
            if (method == HEAD) {
                reason += " To obtain headers, use GET with "
                        + "'driverProxyEnabled' set to 'true'.";
            }
            return HttpFetchResponseBuilder.unsupported().setReasonPhrase(
                  reason).create();
        }

	    LOG.debug("Fetching document: {}", doc.getReference());

	    if (httpSniffer != null) {
	        httpSniffer.bind(doc.getReference());
	    }

        doc.setInputStream(
                fetchDocumentContent(driverTL.get(), doc.getReference()));

        IHttpFetchResponse response = resolveDriverResponse(doc);

        if (screenshotHandler != null) {
            screenshotHandler.takeScreenshot(driverTL.get(), doc);
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

    // Overwrite to perform more advanced configuration/manipulation.
    // thread-safe
    protected InputStream fetchDocumentContent(WebDriver driver, String url) {
        driver.get(url);

        if (StringUtils.isNotBlank(cfg.getPageScript())) {
            ((JavascriptExecutor) driver).executeScript(cfg.getPageScript());
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
        cfg.loadFromXML(xml);
        xml.ifXML("screenshot", x -> {
            ScreenshotHandler h =
                    new ScreenshotHandler(streamFactory);
            h.loadFromXML(x);
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