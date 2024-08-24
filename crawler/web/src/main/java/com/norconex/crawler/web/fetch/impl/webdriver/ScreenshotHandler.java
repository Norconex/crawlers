/* Copyright 2019-2024 Norconex Inc.
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

import static java.util.Optional.ofNullable;

import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.img.MutableImage;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.web.fetch.util.DocImageHandler;
import com.norconex.importer.doc.Doc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

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
 *   {@nx.include com.norconex.crawler.web.fetch.util.DocImageHandler@nx.xml.usage}
 * }
 *
 * <p>
 * The above XML configurable options can be nested in a supporting parent
 * tag of any name.
 * The expected parent tag name is defined by the consuming classes
 * (e.g. "screenshot").
 * </p>
 *
 * @since 3.0.0
 */
@SuppressWarnings("javadoc")
@ToString
@EqualsAndHashCode
@Slf4j
public class ScreenshotHandler
        implements Configurable<ScreenshotHandlerConfig> {

    @Getter
    private final ScreenshotHandlerConfig configuration =
            new ScreenshotHandlerConfig();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private final CachedStreamFactory streamFactory;

    public ScreenshotHandler() {
        this(null);
    }
    public ScreenshotHandler(CachedStreamFactory streamFactory) {
        this.streamFactory = Optional.ofNullable(
                streamFactory).orElseGet(CachedStreamFactory::new);
    }

    public void takeScreenshot(WebDriver driver, Doc doc) {
        var imageHandler = new DocImageHandler();
        imageHandler.setConfiguration(configuration);

        try (InputStream in = streamFactory.newInputStream(
                new ByteArrayInputStream(((TakesScreenshot) driver)
                        .getScreenshotAs(OutputType.BYTES)))) {

            // If wanting a specific web element:
            if (StringUtils.isNotBlank(configuration.getCssSelector())) {
                var element = driver.findElement(
                        By.cssSelector(configuration.getCssSelector()));

                var location = element.getLocation();
                var size = element.getSize();
                var rectangle = new Rectangle(
                        location.x, location.y, size.width, size.height);
                var img = new MutableImage(in);
                img.crop(rectangle);
                imageHandler.handleImage(img.toInputStream(
                        ofNullable(getConfiguration()
                                .getImageFormat()).orElse("png")), doc);
            } else {
                imageHandler.handleImage(in, doc);
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.error("Could not take screenshot of: {}",
                        doc.getReference(), e);
            } else {
                LOG.error("Could not take screenshot of: {}. Error:\n{}",
                        doc.getReference(),
                        ExceptionUtil.getFormattedMessages(e));
            }
        }
    }
}
