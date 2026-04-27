/* Copyright 2019-2026 Norconex Inc.
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

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import com.norconex.commons.lang.img.MutableImage;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.web.fetch.util.AbstractScreenshotHandler;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Takes screenshot of pages using a Selenium {@link WebDriver}.
 * Either the entire page, or a specific DOM element.
 * Screenshot images can be stored in a document metadata/field or
 * in a local directory.
 * </p>
 * @since 3.0.0
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class WebDriverScreenshotHandler extends AbstractScreenshotHandler<WebDriver> {

    public WebDriverScreenshotHandler() {
        super();
    }

    public WebDriverScreenshotHandler(CachedStreamFactory streamFactory) {
        super(streamFactory);
    }

    @Override
    protected byte[] captureScreenshotBytes(WebDriver driver) throws Exception {
        var fullPage = ((TakesScreenshot) driver)
                .getScreenshotAs(OutputType.BYTES);

        if (StringUtils.isNotBlank(getConfiguration().getCssSelector())) {
            var element = driver.findElement(
                    By.cssSelector(getConfiguration().getCssSelector()));
            var location = element.getLocation();
            var size = element.getSize();
            var rectangle = new Rectangle(
                    location.x, location.y, size.width, size.height);
            var img = new MutableImage(new ByteArrayInputStream(fullPage));
            img.crop(rectangle);
            try (var cropped = img.toInputStream(
                    ofNullable(getConfiguration().getImageFormat())
                            .orElse("png"))) {
                return cropped.readAllBytes();
            }
        }
        return fullPage;
    }
}
