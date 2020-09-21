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
import java.nio.file.Path;
import java.util.Map.Entry;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
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

    private Browser browser = Browser.FIREFOX;
    private Path driverPath;
    private Path browserPath;

    private HttpSnifferConfig httpSnifferConfig;

    private final MutableCapabilities capabilities = new MutableCapabilities();

    private Dimension windowSize;

    private String initScript;
    private String pageScript;

    private long pageLoadTimeout;
    private long implicitlyWait;
    private long scriptTimeout;

    public WebDriverHttpFetcherConfig() {
        super();
    }

    public Browser getBrowser() {
        return browser;
    }
    public void setBrowser(Browser driverName) {
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
    public void loadFromXML(XML xml) {
        setBrowser(xml.getEnum("browser", Browser.class, browser));
        setDriverPath(xml.getPath("driverPath", driverPath));
        setBrowserPath(xml.getPath("browserPath", browserPath));

        xml.ifXML("httpSniffer", x -> {
            HttpSnifferConfig cfg = new HttpSnifferConfig();
            cfg.loadFromXML(x);
            //x.populate(cfg);
            setHttpSnifferConfig(cfg);
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
    public void saveToXML(XML xml) {
        xml.addElement("browser", browser);
        xml.addElement("driverPath", driverPath);
        xml.addElement("browserPath", browserPath);

        if (httpSnifferConfig != null) {
            httpSnifferConfig.saveToXML(xml.addElement("httpSniffer"));
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