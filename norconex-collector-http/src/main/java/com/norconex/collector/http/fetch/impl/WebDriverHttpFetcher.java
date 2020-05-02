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
package com.norconex.collector.http.fetch.impl;

import static com.norconex.collector.http.fetch.HttpMethod.GET;
import static com.norconex.collector.http.fetch.HttpMethod.HEAD;
import static java.util.Optional.ofNullable;

import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.remote.service.DriverService;
import org.openqa.selenium.remote.service.DriverService.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.doc.HttpCrawlState;
import com.norconex.collector.http.fetch.AbstractHttpFetcher;
import com.norconex.collector.http.fetch.HttpFetchException;
import com.norconex.collector.http.fetch.HttpFetchResponseBuilder;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.fetch.impl.WebDriverHttpSniffer.DriverResponseFilter;
import com.norconex.collector.http.fetch.util.ApacheHttpUtil;
import com.norconex.commons.lang.SLF4JUtil;
import com.norconex.commons.lang.Sleeper;
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
 * <fetcher class="com.norconex.collector.http.fetch.impl.WebDriverHttpFetcher">
 *
 *   <browser>[chrome|edge|firefox|opera|safari]</browser>
 *   <browserPath>(browser executable or blank to detect)</browserPath>
 *   <driverPath>(driver executable or blank to detect)</driverPath>
 *   <servicePort>(default is 0 = random free port)</servicePort>
 *
 *   <!-- Optional browser capabilities supported by the web driver. -->
 *   <capabilities>
 *     <capability name="(capability name)">(capability value)</capability>
 *     <!-- multiple "capability" tags allowed -->
 *   </capabilities>
 *
 *   <!-- Optionally take screenshots of each web pages. -->
 *   <screenshot>
 *     <capability name="(capability name)">(capability value)</capability>
 *     <!-- multiple "capability" tags allowed -->
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
 * <fetcher class="com.norconex.collector.http.fetch.impl.WebDriverHttpFetcher">
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
//TODO implement CollectorLifeCycleListener instead? to ensure one per coll.?
public class WebDriverHttpFetcher extends AbstractHttpFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(
            WebDriverHttpFetcher.class);



    private CachedStreamFactory streamFactory;

    private WebDriverBrowser browser = WebDriverBrowser.FIREFOX;
    private Path driverPath;
    private Path browserPath;
    private String userAgent;
    private int servicePort;   // default is 0 = any free port

    private WebDriverHttpSniffer httpSniffer;
    private WebDriverHttpSnifferConfig httpSnifferConfig;
    private WebDriverScreenshotHandler screenshotHandler;

    private final MutableCapabilities capabilities = new MutableCapabilities();

    private Dimension windowSize;

    private String initScript;
    private String pageScript;

    private long pageLoadTimeout;
    private long implicitlyWait;
    private long scriptTimeout;

    private DriverService service;
    private final ThreadLocal<WebDriver> driverTL = new ThreadLocal<>();

    public WebDriverHttpFetcher() {
        super();
    }

    public WebDriverBrowser getBrowser() {
        return browser;
    }
    public void setBrowser(WebDriverBrowser driverName) {
        this.browser = driverName;
    }

    // Default will try to detect driver installation on OS
    public Path getDriverPath() {
        return driverPath;
    }
    public void setDriverPath(Path driverPath) {
        this.driverPath = driverPath;
    }

    // Default will try to detect browser installation on OS
    public Path getBrowserPath() {
        return browserPath;
    }
    public void setBrowserPath(Path binaryPath) {
        this.browserPath = binaryPath;
    }

    public int getServicePort() {
        return servicePort;
    }

    public void setServicePort(int servicePort) {
        this.servicePort = servicePort;
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }

    public WebDriverHttpSnifferConfig getHttpSnifferConfig() {
        return httpSnifferConfig;
    }

    public void setHttpSnifferConfig(
            WebDriverHttpSnifferConfig httpSnifferConfig) {
        this.httpSnifferConfig = httpSnifferConfig;
    }

    public MutableCapabilities getCapabilities() {
        return capabilities;
    }

    public WebDriverScreenshotHandler getScreenshotHandler() {
        return screenshotHandler;
    }
    public void setScreenshotHandler(
            WebDriverScreenshotHandler screenshotHandler) {
        this.screenshotHandler = screenshotHandler;
    }

    public Dimension getWindowSize() {
        return windowSize;
    }
    public void setWindowSize(Dimension windowSize) {
        this.windowSize = windowSize;
    }

    public String getInitScript() {
        return initScript;
    }
    public void setInitScript(String initScript) {
        this.initScript = initScript;
    }

    public String getPageScript() {
        return pageScript;
    }
    public void setPageScript(String pageScript) {
        this.pageScript = pageScript;
    }

    public long getPageLoadTimeout() {
        return pageLoadTimeout;
    }
    public void setPageLoadTimeout(long pageLoadTimeout) {
        this.pageLoadTimeout = pageLoadTimeout;
    }

    public long getImplicitlyWait() {
        return implicitlyWait;
    }
    public void setImplicitlyWait(long implicitlyWait) {
        this.implicitlyWait = implicitlyWait;
    }

    public long getScriptTimeout() {
        return scriptTimeout;
    }
    public void setScriptTimeout(long scriptTimeout) {
        this.scriptTimeout = scriptTimeout;
    }

    @Override
    protected void crawlerStartup(CrawlerEvent<Crawler> event) {
        LOG.info("Starting {} driver service...", browser);
        //System.setProperty("bmp.allowNativeDnsFallback", "true");

        if (event != null) {
            streamFactory = event.getSource().getStreamFactory();
        } else {
            streamFactory = new CachedStreamFactory();
        }

        //TODO remove support for EDGE since it is so limited???
//        if (browser == WebDriverBrowser.EDGE) {
//            LOG.warn("Using Microsoft Edge is not recommended if you want "
//                   + "to capture HTTP headers, use multiple threads, be "
//                   + "fully headless, or change its 'User-Agent'.");
//        }

        try {
            MutableCapabilities options =
                    browser.createCapabilities(browserPath);

            initWebDriverLogging(options);

            options.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
            options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);

            options.merge(capabilities);

            if (httpSnifferConfig != null) {
                httpSniffer = new WebDriverHttpSniffer();
                httpSniffer.start(options, httpSnifferConfig);
                userAgent = httpSnifferConfig.getUserAgent();
            }

            Builder<?,?> serviceBuilder = browser.createServiceBuilder();
            if (driverPath != null) {
                serviceBuilder.usingDriverExecutable(driverPath.toFile());
            }
            service = serviceBuilder.usingPort(servicePort).build();
            service.start();

            WebDriver driver = new RemoteWebDriver(service.getUrl(), options);
            driverTL.set(driver);

            if (windowSize != null) {
                driver.manage().window().setSize(
                        new org.openqa.selenium.Dimension(
                                windowSize.width, windowSize.height));
            }

            Timeouts timeouts = driver.manage().timeouts();
            if (pageLoadTimeout != 0) {
                timeouts.pageLoadTimeout(
                        pageLoadTimeout, TimeUnit.MILLISECONDS);
            }
            if (implicitlyWait != 0) {
                timeouts.implicitlyWait(implicitlyWait, TimeUnit.MILLISECONDS);
            }
            if (scriptTimeout != 0) {
                timeouts.setScriptTimeout(scriptTimeout, TimeUnit.MILLISECONDS);
            }

            if (StringUtils.isBlank(userAgent)) {
                userAgent = (String) ((JavascriptExecutor) driver).executeScript(
                        "return navigator.userAgent;");
            }

            if (StringUtils.isNotBlank(initScript)) {
                ((JavascriptExecutor) driver).executeScript(initScript);
            }

        } catch (IOException e) {
            //TODO if exception, check if driver and browser were specified
            // either explicitly, as system property, or in PATH?
            throw new CollectorException(
                    "Could not start " + browser + " driver service.", e);
        }
    }

    private void initWebDriverLogging(MutableCapabilities capabilities) {
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
    protected void crawlerShutdown(CrawlerEvent<Crawler> event) {
        LOG.info("Shutting down {} driver service...", browser);

        Sleeper.sleepSeconds(5);

        if (driverTL.get() != null) {
            driverTL.get().quit();
            driverTL.remove();
        }

        if (service != null && service.isRunning()) {
            service.stop();
        }
        if (httpSniffer != null) {
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
                reason += " To obtain headers, use GET with "
                        + "'driverProxyEnabled' set to 'true'.";
            }
            return HttpFetchResponseBuilder.unsupported().setReasonPhrase(
                  reason).create();
        }

//
//
//    }
//
//    @Override
//    public IHttpFetchResponse head(
//            String url, Properties httpHeaders) {
//        //TODO rely on proxy request filter to transform to a HEAD request?
//
//        return HttpFetchResponseBuilder.unsupported()
//                .setReasonPhrase("Headers can only be retrived with "
//                        + "#fetchDocument instead, when driverProxyEnabled "
//                        + "is true.").build();
//    }
//
//    @Override
//    public IHttpFetchResponse get(Doc doc) {

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
        if (StringUtils.isNotBlank(pageScript)) {
            ((JavascriptExecutor) driver).executeScript(pageScript);
        }
        String pageSource = driver.getPageSource();
        return IOUtils.toInputStream(pageSource, StandardCharsets.UTF_8);
    }

    private IHttpFetchResponse resolveDriverResponse(CrawlDoc doc) {
        if (httpSniffer == null) {
            return null;
        }
        IHttpFetchResponse response = null;
        DriverResponseFilter driverResponseFilter = httpSniffer.unbind();
        if (driverResponseFilter != null) {
            for (Entry<String, String> en : driverResponseFilter.getHeaders()) {
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
        setBrowser(xml.getEnum("browser", WebDriverBrowser.class, browser));
        setDriverPath(xml.getPath("driverPath", driverPath));
        setBrowserPath(xml.getPath("browserPath", browserPath));
        setServicePort(xml.getInteger("servicePort", servicePort));

        xml.ifXML("httpSniffer", x -> {
            WebDriverHttpSnifferConfig cfg = new WebDriverHttpSnifferConfig();
            cfg.loadFromXML(x);
            setHttpSnifferConfig(cfg);
        });

        xml.ifXML("screenshot", x -> {
            WebDriverScreenshotHandler h =
                    new WebDriverScreenshotHandler(streamFactory);
            h.loadFromXML(x);
            setScreenshotHandler(h);
        });

        for (Entry<String, String> en : xml.getStringMap(
                "capabilities/capability", "@name", ".").entrySet()) {
            getCapabilities().setCapability(en.getKey(), en.getValue());
        }

        setWindowSize(xml.getDimension("windowSize", windowSize));
        setInitScript(xml.getString("initScript", initScript));
        setPageScript(xml.getString("pageScript", pageScript));
        setPageLoadTimeout(
                xml.getDurationMillis("pageLoadTimeout", pageLoadTimeout));
        setImplicitlyWait(
                xml.getDurationMillis("implicitlyWait", implicitlyWait));
        setScriptTimeout(xml.getDurationMillis("scriptTimeout", scriptTimeout));
    }
    @Override
    public void saveHttpFetcherToXML(XML xml) {
        xml.addElement("browser", browser);
        xml.addElement("driverPath", driverPath);
        xml.addElement("browserPath", browserPath);
        xml.addElement("servicePort", servicePort);

        if (httpSnifferConfig != null) {
            httpSnifferConfig.saveToXML(xml.addElement("httpSniffer"));
        }

        if (screenshotHandler != null) {
            screenshotHandler.saveToXML(xml.addElement("screenshot"));
        }

        XML capabXml = xml.addElement("capabilities");
        for (Entry<String, Object> en : capabilities.asMap().entrySet()) {
            capabXml.addElement("capability",
                    en.getValue()).setAttribute("name", en.getKey());
        }

        xml.addElement("windowSize", windowSize);
        xml.addElement("initScript", initScript);
        xml.addElement("pageScript", pageScript);
        xml.addElement("pageLoadTimeout", pageLoadTimeout);
        xml.addElement("implicitlyWait", implicitlyWait);
        xml.addElement("scriptTimeout", scriptTimeout);
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