/* Copyright 2019-2020 Norconex Inc.
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

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringExclude;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDocMetadata;
import com.norconex.collector.http.fetch.util.DocImageHandler;
import com.norconex.commons.lang.img.MutableImage;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.Doc;

/**
 * <p>
 * Takes screenshot of pages using a Selenium {@link WebDriver}.
 * Either the entire page, or a specific DOM element.
 * Screenshot images can be stored in a document metadata/field or
 * in a local directory.
 * </p>
 *
 * {@nx.xml.usage
 *   <cssSelector>(Optional selector of element to capture.)</cssSelector>
 *   {@nx.include com.norconex.collector.http.fetch.util.DocImageHandler@nx.xml.usage}
 * }
 *
 * <p>
 * The above XML configurable options can be nested in a supporting parent
 * tag of any name.
 * The expected parent tag name is defined by the consuming classes
 * (e.g. "screenshot").
 * </p>
 *
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public class ScreenshotHandler extends DocImageHandler {

    public static final Path DEFAULT_SCREENSHOT_DIR =
            Paths.get("./screenshots");
    public static final String DEFAULT_SCREENSHOT_DIR_FIELD =
            CrawlDocMetadata.PREFIX + "screenshot-path";
    public static final String DEFAULT_SCREENSHOT_META_FIELD =
            CrawlDocMetadata.PREFIX + "screenshot";

    private static final Logger LOG = LoggerFactory.getLogger(
            ScreenshotHandler.class);

    private String cssSelector;
    @ToStringExclude
    @HashCodeExclude
    @EqualsExclude
    private final CachedStreamFactory streamFactory;

    public ScreenshotHandler() {
        this(null);
    }
    public ScreenshotHandler(CachedStreamFactory streamFactory) {
        super(DEFAULT_SCREENSHOT_DIR,
             DEFAULT_SCREENSHOT_DIR_FIELD,
             DEFAULT_SCREENSHOT_META_FIELD);
        this.streamFactory = Optional.ofNullable(
                streamFactory).orElseGet(CachedStreamFactory::new);
    }
    public String getCssSelector() {
        return cssSelector;
    }
    public void setCssSelector(String cssSelector) {
        this.cssSelector = cssSelector;
    }

    public void takeScreenshot(WebDriver driver, Doc doc) {
        try (InputStream in = streamFactory.newInputStream(
                new ByteArrayInputStream(((TakesScreenshot) driver)
                        .getScreenshotAs(OutputType.BYTES)))) {

            // If wanting a specific web element:
            if (StringUtils.isNotBlank(cssSelector)) {
                WebElement element = driver.findElement(
                        By.cssSelector(cssSelector));

                Point location = element.getLocation();
                org.openqa.selenium.Dimension size = element.getSize();
                Rectangle rectangle = new Rectangle(
                        location.x, location.y, size.width, size.height);
                MutableImage img = new MutableImage(in);
                img.crop(rectangle);
                handleImage(img.toInputStream(Optional.ofNullable(
                        getImageFormat()).orElse("png")), doc);
            } else {
                handleImage(in, doc);
            }
        } catch (Exception e) {
            LOG.error("Could not take screenshot of: {}",
                    doc.getReference(), e);
        }
    }

    @Override
    public void loadFromXML(XML xml) {
        super.loadFromXML(xml);
        setCssSelector(xml.getString("cssSelector", cssSelector));
    }
    @Override
    public void saveToXML(XML xml) {
        super.saveToXML(xml);
        xml.addElement("cssSelector", cssSelector);
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
