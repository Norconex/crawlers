/* Copyright 2020-2023 Norconex Inc.
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

import java.awt.Dimension;
import java.net.URL;
import java.nio.file.Path;
import java.util.function.Function;

import org.openqa.selenium.By;
import org.openqa.selenium.MutableCapabilities;

import com.norconex.crawler.core.fetch.BaseFetcherConfig;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link WebDriverHttpFetcher}.
 * </p>
 * @see WebDriverHttpFetcher
 * @since 3.0.0
 */
@Data
@Accessors(chain = true)
public class WebDriverHttpFetcherConfig extends BaseFetcherConfig {

    public enum WaitElementType {
        TAGNAME(By::tagName),
        CLASSNAME(By::className),
        CSSSELECTOR(By::cssSelector),
        ID(By::id),
        LINKTEXT(By::linkText),
        NAME(By::name),
        PARTIALLINKTEXT(By::partialLinkText),
        XPATH(By::xpath);
        private final Function<String, By> byFunction;
        WaitElementType(Function<String, By> byFunction) {
            this.byFunction = byFunction;
        }
        By getBy(String selector) {
            return byFunction.apply(selector);
        }
    }

    private Browser browser = Browser.FIREFOX;
    // Default will try to detect driver installation on OS
    private Path driverPath;
    // Default will try to detect browser installation on OS
    private Path browserPath;
    private URL remoteURL;

    private HttpSniffer httpSniffer;
    private ScreenshotHandler screenshotHandler;

    private final MutableCapabilities capabilities = new MutableCapabilities();

    private Dimension windowSize;

    private String earlyPageScript;
    private String latePageScript;

    private long pageLoadTimeout;
    private long implicitlyWait;
    private long scriptTimeout;
    private long threadWait;

    private WaitElementType waitForElementType;
    private String waitForElementSelector;
    private long waitForElementTimeout;

//    @Override
//    public void loadFromXML(XML xml) {
//        setBrowser(xml.getEnum("browser", Browser.class, browser));
//        setDriverPath(xml.getPath("driverPath", driverPath));
//        setBrowserPath(xml.getPath("browserPath", browserPath));
//        setRemoteURL(xml.getURL("remoteURL", remoteURL));
//
//        xml.ifXML("httpSniffer", x -> {
//            var cfg = new HttpSnifferConfig();
//            cfg.loadFromXML(x);
//            //x.populate(cfg);
//            setHttpSnifferConfig(cfg);
//        });
//
//        for (Entry<String, String> en : xml.getStringMap(
//                "capabilities/capability", "@name", ".").entrySet()) {
//            getCapabilities().setCapability(en.getKey(), en.getValue());
//        }
//
//        setWindowSize(xml.getDimension("windowSize", windowSize));
//        setEarlyPageScript(xml.getString("earlyPageScript", earlyPageScript));
//        setLatePageScript(xml.getString("latePageScript", latePageScript));
//        setPageLoadTimeout(
//                xml.getDurationMillis("pageLoadTimeout", pageLoadTimeout));
//        setImplicitlyWait(
//                xml.getDurationMillis("implicitlyWait", implicitlyWait));
//        setScriptTimeout(xml.getDurationMillis("scriptTimeout", scriptTimeout));
//        setWaitForElementType(xml.getEnum("waitForElement/@type",
//                WaitElementType.class, waitForElementType));
//        setWaitForElementSelector(xml.getString("waitForElement/@selector",
//                waitForElementSelector));
//        setWaitForElementTimeout(xml.getDurationMillis("waitForElement",
//                waitForElementTimeout));
//        setThreadWait(xml.getDurationMillis("threadWait", threadWait));
//    }
//
//    @Override
//    public void saveToXML(XML xml) {
//        xml.addElement("browser", browser);
//        xml.addElement("driverPath", driverPath);
//        xml.addElement("browserPath", browserPath);
//        xml.addElement("remoteURL", remoteURL);
//
//        if (httpSnifferConfig != null) {
//            httpSnifferConfig.saveToXML(xml.addElement("httpSniffer"));
//        }
//
//        var capabXml = xml.addElement("capabilities");
//        for (Entry<String, Object> en : capabilities.asMap().entrySet()) {
//            capabXml.addElement("capability",
//                    en.getValue()).setAttribute("name", en.getKey());
//        }
//
//        xml.addElement("windowSize", windowSize);
//        xml.addElement("earlyPageScript", earlyPageScript);
//        xml.addElement("latePageScript", latePageScript);
//        xml.addElement("pageLoadTimeout", pageLoadTimeout);
//        xml.addElement("implicitlyWait", implicitlyWait);
//        xml.addElement("scriptTimeout", scriptTimeout);
//        xml.addElement("waitForElement", waitForElementTimeout)
//                .setAttribute("type", waitForElementType)
//                .setAttribute("selector", waitForElementSelector);
//        xml.addElement("threadWait", threadWait);
//    }
}