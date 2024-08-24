/* Copyright 2020 Norconex Inc.
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

import java.awt.Dimension;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.openqa.selenium.By;
import org.openqa.selenium.MutableCapabilities;

import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Configuration for {@link WebDriverHttpFetcher}.
 * </p>
 * @see WebDriverHttpFetcher
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class WebDriverHttpFetcherConfig implements IXMLConfigurable {

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
    private Path driverPath;
    private Path browserPath;
    private URL remoteURL;

    private HttpSnifferConfig httpSnifferConfig;

    private final MutableCapabilities capabilities = new MutableCapabilities();
    private final List<String> arguments = new ArrayList<>();

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

    public WebDriverHttpFetcherConfig() {
    }

    public Browser getBrowser() {
        return browser;
    }
    public void setBrowser(Browser driverName) {
        browser = driverName;
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
        browserPath = binaryPath;
    }

    public HttpSnifferConfig getHttpSnifferConfig() {
        return httpSnifferConfig;
    }

    public void setHttpSnifferConfig(
            HttpSnifferConfig httpSnifferConfig) {
        this.httpSnifferConfig = httpSnifferConfig;
    }

    public MutableCapabilities getCapabilities() {
        return capabilities;
    }

    /**
     * Gets optional arguments passed to the browser if it supports arguments.
     * @return arguments
     * @since 3.1.0
     */
    public List<String> getArguments() {
        return arguments;
    }

    public Dimension getWindowSize() {
        return windowSize;
    }
    public void setWindowSize(Dimension windowSize) {
        this.windowSize = windowSize;
    }

    public String getEarlyPageScript() {
        return earlyPageScript;
    }
    public void setEarlyPageScript(String initScript) {
        earlyPageScript = initScript;
    }

    public String getLatePageScript() {
        return latePageScript;
    }
    public void setLatePageScript(String pageScript) {
        latePageScript = pageScript;
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

    public WaitElementType getWaitForElementType() {
        return waitForElementType;
    }
    public void setWaitForElementType(WaitElementType waitForElementType) {
        this.waitForElementType = waitForElementType;
    }

    public String getWaitForElementSelector() {
        return waitForElementSelector;
    }
    public void setWaitForElementSelector(String waitForElementSelector) {
        this.waitForElementSelector = waitForElementSelector;
    }

    public long getWaitForElementTimeout() {
        return waitForElementTimeout;
    }
    public void setWaitForElementTimeout(long waitForElementTimeout) {
        this.waitForElementTimeout = waitForElementTimeout;
    }

    public long getThreadWait() {
        return threadWait;
    }
    public void setThreadWait(long threadWait) {
        this.threadWait = threadWait;
    }

    public URL getRemoteURL() {
        return remoteURL;
    }
    public void setRemoteURL(URL remoteURL) {
        this.remoteURL = remoteURL;
    }

    @Override
    public void loadFromXML(XML xml) {
        setBrowser(xml.getEnum("browser", Browser.class, browser));
        setDriverPath(xml.getPath("driverPath", driverPath));
        setBrowserPath(xml.getPath("browserPath", browserPath));
        setRemoteURL(xml.getURL("remoteURL", remoteURL));

        xml.ifXML("httpSniffer", x -> {
            var cfg = new HttpSnifferConfig();
            cfg.loadFromXML(x);
            setHttpSnifferConfig(cfg);
        });

        for (Entry<String, String> en : xml.getStringMap(
                "capabilities/capability", "@name", ".").entrySet()) {
            getCapabilities().setCapability(en.getKey(), en.getValue());
        }
        arguments.addAll(xml.getStringList(
                "arguments/arg", getArguments()));

        setWindowSize(xml.getDimension("windowSize", windowSize));
        setEarlyPageScript(xml.getString("earlyPageScript", earlyPageScript));
        setLatePageScript(xml.getString("latePageScript", latePageScript));
        setPageLoadTimeout(
                xml.getDurationMillis("pageLoadTimeout", pageLoadTimeout));
        setImplicitlyWait(
                xml.getDurationMillis("implicitlyWait", implicitlyWait));
        setScriptTimeout(xml.getDurationMillis("scriptTimeout", scriptTimeout));
        setWaitForElementType(xml.getEnum("waitForElement/@type",
                WaitElementType.class, waitForElementType));
        setWaitForElementSelector(xml.getString("waitForElement/@selector",
                waitForElementSelector));
        setWaitForElementTimeout(xml.getDurationMillis("waitForElement",
                waitForElementTimeout));
        setThreadWait(xml.getDurationMillis("threadWait", threadWait));
    }

    @Override
    public void saveToXML(XML xml) {
        xml.addElement("browser", browser);
        xml.addElement("driverPath", driverPath);
        xml.addElement("browserPath", browserPath);
        xml.addElement("remoteURL", remoteURL);

        if (httpSnifferConfig != null) {
            httpSnifferConfig.saveToXML(xml.addElement("httpSniffer"));
        }

        var capabXml = xml.addElement("capabilities");
        for (Entry<String, Object> en : capabilities.asMap().entrySet()) {
            capabXml.addElement("capability",
                    en.getValue()).setAttribute("name", en.getKey());
        }
        var argumentsXml = xml.addElement("arguments");
        for (String argument : arguments) {
            argumentsXml.addElement("arg", argument);
        }
        xml.addElement("windowSize", windowSize);
        xml.addElement("earlyPageScript", earlyPageScript);
        xml.addElement("latePageScript", latePageScript);
        xml.addElement("pageLoadTimeout", pageLoadTimeout);
        xml.addElement("implicitlyWait", implicitlyWait);
        xml.addElement("scriptTimeout", scriptTimeout);
        xml.addElement("waitForElement", waitForElementTimeout)
                .setAttribute("type", waitForElementType)
                .setAttribute("selector", waitForElementSelector);
        xml.addElement("threadWait", threadWait);
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