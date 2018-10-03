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
import java.nio.file.Paths;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.client.HttpClient;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.crawler.CrawlerLifeCycleListener;
import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

import net.lightbody.bmp.BrowserMobProxy;
import net.lightbody.bmp.BrowserMobProxyServer;
import net.lightbody.bmp.client.ClientUtil;
import net.lightbody.bmp.core.har.Har;
import net.lightbody.bmp.core.har.HarEntry;
import net.lightbody.bmp.core.har.HarNameValuePair;
import net.lightbody.bmp.proxy.CaptureType;

/**
 * <h2>Experimental</h2>
 * <p>
 * Uses Selenium WebDriver support for using native browsers to crawl documents.
 * </p>
 * <p>
 * <b>In heavy development. Will change.</b>
 * </p>
 * @author Pascal Essiembre
 * @since 3.0.0
 */
//TODO implement CollectorLifeCycleListener instead? and ensure one per coll.?
public class WebDriverDocumentFetcher extends CrawlerLifeCycleListener
        implements IHttpDocumentFetcher, IXMLConfigurable {

    private static final Logger LOG = LoggerFactory.getLogger(
            WebDriverDocumentFetcher.class);

    private String driverName;  // default to HTMLUnit which can be shipped with collector?
    private final Path driverPath = Paths.get("C:\\Apps\\chromedriver\\2.42\\chromedriver.exe");
    private Path binaryPath;  // browser path?

    private ChromeDriverService service;
    private WebDriver driver;

    private BrowserMobProxy proxy;

    //TODO several things:
    // - Support complext configuration via JavaScript?  Like authenticating
    //   scripts populating fields and submitting pages.

    // driver path (will use it from PATH if there)
    // chrome path (will use default location if there)
    // driver (chrome, firefox, etc)
/*
  Maybe rename httpRequestExecutors or webRequesters or httpExecutors
  or httpFetchers or httpCallers or httpInvokers or httpRequesters

  merge httpClient with the GenericDocFetcher (as it does not apply to all)

  <!--
    In order of execution order. If first fails or does not support given
    URL, it moves on to the next one.
    -->
  <httpFetchers>
    <fetcher name="DefaultFetcher" class="GenericFetcher" maxRetries="3">
      <restrictTo field="contentType">text/html</restrictTo>
      <restrictTo field="document.reference">.html</restrictTo>
      <restrictTo>...</restrictTo>
      <fallbackFetcher name="PhantomJSFetcher"/>
    </fetcher>
    <fetcher name="DefaultFetcher" class="GenericFetcher" maxRetries="3">
      <restrictTo field="document.reference">.php</restrictTo>
    </fetcher>
  </httpFetchers>

*/
    // Maybe for PDF: https://github.com/GoogleChrome/puppeteer/issues/1872#issuecomment-401523623

    //TODO document that configurable items should have contstructor empty
    // and rely on init/destroy equivalent using event listeners
    public WebDriverDocumentFetcher() {
        super();
    }



    @Override
    protected void crawlerStartup(CrawlerEvent<Crawler> event) {
        LOG.info("Starting chrome driver service...");
        System.setProperty("bmp.allowNativeDnsFallback", "true");
        System.setProperty("webdriver.chrome.driver", driverPath.toString());
        try {

            // START PROXY TEST
            proxy = new BrowserMobProxyServer();
            proxy.start(0);
            int port = proxy.getPort(); // get the JVM-assigned port
            Proxy seleniumProxy = ClientUtil.createSeleniumProxy(proxy);
            // STOP PROXY TEST


            ChromeOptions options = new ChromeOptions();
            if (binaryPath != null) {
                options.setBinary(binaryPath.toFile());
            }
            options.setHeadless(true);

            // START PROXY TEST
            options.setCapability(CapabilityType.PROXY, seleniumProxy);
            options.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
            // STOP PROXY TEST

            service = new ChromeDriverService.Builder()
                    .usingDriverExecutable(driverPath.toFile())
                    .usingAnyFreePort()
                    .build();
            service.start();
            driver = new RemoteWebDriver(service.getUrl(), options);


            // START PROXY TEST
            // enable more detailed HAR capture, if desired (see CaptureType for the complete list)
            proxy.enableHarCaptureTypes(CaptureType.getHeaderCaptureTypes());
                    //CaptureType.REQUEST_CONTENT, CaptureType.RESPONSE_CONTENT);
            // STOP PROXY TEST
        } catch (IOException e) {
            throw new CollectorException(
                    "Could not start chrome drier service.", e);
        }
    }
    @Override
    protected void crawlerShutdown(CrawlerEvent<Crawler> event) {
        LOG.info("Shutting down chrome driver service...");

        Sleeper.sleepSeconds(5);

        driver.quit();

        if (service.isRunning()) {
            service.stop();
        }
        proxy.stop();
    }

    //TODO replace signature with Writer class??
    @Override
	public HttpFetchResponse fetchDocument(
	        HttpClient httpClient, HttpDocument doc) {

//        ChromeDriverService.


	    LOG.debug("Fetching document: {}", doc.getReference());


        // START PROXY TEST
        // create a new HAR with the label "yahoo.com"
        proxy.newHar("PascalTest" + System.currentTimeMillis());
        // STOP PROXY TEST

        driver.get(doc.getReference());

        // START PROXY TEST
        // get the HAR data
        Har har = proxy.getHar();
//        har.getLog().getEntries()
        HarEntry harEntry = har.getLog().getEntries().get(0);
        //System.out.println("HAR: " + har.getLog().getEntries());
        for (HarNameValuePair pair : harEntry.getRequest().getHeaders()) {
            doc.getMetadata().add(pair.getName(), pair.getValue());
        }
        for (HarNameValuePair pair : harEntry.getResponse().getHeaders()) {
            doc.getMetadata().add(pair.getName(), pair.getValue());
        }
        // STOP PROXY TEST

        String pageSource = driver.getPageSource();

        doc.setContent(doc.getContent().newInputStream(IOUtils.toInputStream(pageSource, StandardCharsets.UTF_8)));

//      //read a copy to force caching and then close the HTTP stream
        try {
            IOUtils.copy(doc.getContent(), new NullOutputStream());
//System.out.println("CONTENT: " + IOUtils.toString(doc.getContent()));

        } catch (IOException e) {
            throw new CollectorException("Could not read page content for: " + doc.getReference(), e);
        }


//
//      performDetection(doc);
      return new HttpFetchResponse(
              HttpCrawlState.NEW, 200, "TODO: get response code and headers from log.");



      //---- OPTIONS/TODO ------------
      /*

//TODO set/get HTTP Headers with: https://github.com/lightbody/browsermob-proxy#using-with-selenium
                              or: https://stackoverflow.com/questions/6509628/how-to-get-http-response-code-using-selenium-webdriver

https://ksah.in/introduction-to-chrome-headless/
https://seleniumhq.github.io/docs/
https://seleniumhq.github.io/selenium/docs/api/java/index.html
https://github.com/ldaume/headless-chrome
https://docs.seleniumhq.org/docs/04_webdriver_advanced.jsp#explicit-and-implicit-waits
https://www.seleniumhq.org/download/maven.jsp
https://thefriendlytester.co.uk/2017/04/new-headless-chrome-with-selenium.html
https://chercher.tech/java/headless-browsers-selenium-webdriver
https://developers.google.com/web/updates/2017/04/headless-chrome
https://sites.google.com/a/chromium.org/chromedriver/getting-started
https://dzone.com/articles/running-selenium-tests-with-chrome-headless
https://www.codeproject.com/Articles/843207/Automated-Testing-Of-Web-Pages-Using-Selenium-Web
https://sites.google.com/a/chromium.org/chromedriver/capabilities
https://github.com/SeleniumHQ/selenium/blob/07a18746ff756e90fd79ef253a328bd7dfa9e6dc/java/client/src/org/openqa/selenium/firefox/FirefoxBinary.java

chrome --headless --disable-gpu --screenshot https://www.chromestatus.com/
https://www.epa.gov/sites/production/files/2017-12/documents/lcr_federalism_consultation_letter.signed.12_14_17.pdf
chrome --headless --disable-gpu --screenshot https://mozilla.github.io/pdf.js/web/viewer.html

IWait<IWebDriver> wait = new OpenQA.Selenium.Support.UI.WebDriverWait(driver, TimeSpan.FromSeconds(30.00));

 wait.Until(driver1 => ((IJavaScriptExecutor)driver).ExecuteScript("return document.readyState").Equals("complete"));


//      System.setProperty("webdriver.chrome.driver", driverPath.toString());
//      WebDriver driver = new ChromeDriver(options);
//      ChromeOptions options = new ChromeOptions();
//      if (binaryPath != null) {
//          options.setBinary(binaryPath.toFile());
//      }
//      options.setHeadless(true);

//        driver.get("http://www.google.com?q=test");
        Sleeper.sleepSeconds(2); // Let the user actually see something!
//        WebElement searchBox = driver.findElement(By.name("q"));
//        searchBox.sendKeys("ChromeDriver");
//        searchBox.submit();
//        Thread.sleep(5000);  // Let the user actually see something!

//        System.out.println("SOURCE:\n" + driver.getPageSource());

//        driver.quit();














      options.addArguments("headless");
      options.addArguments("window-size=1200x600");
      WebDriver driver = new ChromeDriver(options);
      driver.get("http://seleniumhq.org");
      // a guarantee that the test was really executed
      assertTrue(driver.findElement(By.id("q")).isDisplayed());

https://sites.google.com/a/chromium.org/chromedriver/capabilities

experimental:
Map<String, Object> prefs = new HashMap<String, Object>();
prefs.put("profile.default_content_settings.popups", 0);
options.setExperimentalOption("prefs", prefs);











      */


//	    HttpRequestBase method = null;
//	    try {
//	        method = createUriRequest(doc);
//
//	        // Execute the method.
//            HttpResponse response = httpClient.execute(method);
//            int statusCode = response.getStatusLine().getStatusCode();
//            String reason = response.getStatusLine().getReasonPhrase();
//
//            InputStream is = response.getEntity().getContent();
//
//            // VALID http response
//            if (validStatusCodes.contains(statusCode)) {
//                //--- Fetch headers ---
//                Header[] headers = response.getAllHeaders();
//                for (int i = 0; i < headers.length; i++) {
//                    Header header = headers[i];
//                    String name = header.getName();
//                    if (StringUtils.isNotBlank(headersPrefix)) {
//                        name = headersPrefix + name;
//                    }
//                    if (doc.getMetadata().getString(name) == null) {
//                        doc.getMetadata().add(name, header.getValue());
//                    }
//                }
//
//                //--- Fetch body
//                doc.setContent(doc.getContent().newInputStream(is));
//
//                //read a copy to force caching and then close the HTTP stream
//                IOUtils.copy(doc.getContent(), new NullOutputStream());
//
//                performDetection(doc);
//                return new HttpFetchResponse(
//                        HttpCrawlState.NEW, statusCode, reason);
//            }
//
//            // INVALID http response
//            if (LOG.isTraceEnabled()) {
//                LOG.trace("Rejected response content: "
//                        + IOUtils.toString(is, StandardCharsets.UTF_8));
//                IOUtils.closeQuietly(is);
//            } else {
//                // read response anyway to be safer, but ignore content
//                BufferedInputStream bis = new BufferedInputStream(is);
//                int result = bis.read();
//                while(result != -1) {
//                  result = bis.read();
//                }
//                IOUtils.closeQuietly(bis);
//            }
//
//            if (notFoundStatusCodes.contains(statusCode)) {
//                return new HttpFetchResponse(
//                        HttpCrawlState.NOT_FOUND, statusCode, reason);
//            }
//            LOG.debug("Unsupported HTTP Response: "
//                    + response.getStatusLine());
//            return new HttpFetchResponse(
//                    CrawlState.BAD_STATUS, statusCode, reason);
//        } catch (Exception e) {
//            if (LOG.isDebugEnabled()) {
//                LOG.info("Cannot fetch document: " + doc.getReference()
//                        + " (" + e.getMessage() + ")", e);
//            } else {
//                LOG.info("Cannot fetch document: " + doc.getReference()
//                        + " (" + e.getMessage() + ")");
//            }
//            throw new CollectorException(e);
//        } finally {
//            if (method != null) {
//                method.releaseConnection();
//            }
//        }
	}

    @Override
    public void loadFromXML(XML xml) {
//        setValidStatusCodes(xml.getDelimitedList(
//                "validStatusCodes", Integer.class, validStatusCodes));
//        setNotFoundStatusCodes(xml.getDelimitedList(
//                "notFoundStatusCodes", Integer.class, notFoundStatusCodes));
//        setHeadersPrefix(xml.getString("headersPrefix"));
//        setDetectContentType(
//                xml.getBoolean("@detectContentType", detectContentType));
//        setDetectCharset(xml.getBoolean("@detectCharset", detectCharset));
    }
    @Override
    public void saveToXML(XML xml) {
//        xml.setAttribute("detectContentType", detectContentType);
//        xml.setAttribute("detectCharset", detectCharset);
//        xml.addDelimitedElementList("validStatusCodes", validStatusCodes);
//        xml.addDelimitedElementList("notFoundStatusCodes", notFoundStatusCodes);
//        xml.addElement("headersPrefix", headersPrefix);
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