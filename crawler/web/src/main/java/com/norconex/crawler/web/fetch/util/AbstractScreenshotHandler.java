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
package com.norconex.crawler.web.fetch.util;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.importer.doc.Doc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Base class for screenshot handlers that capture browser page images during
 * crawling. Handles the common infrastructure: stream management,
 * {@link DocImageHandler} wiring, and error handling.
 * </p>
 * <p>
 * Concrete subclasses implement {@link #captureScreenshotBytes(Object)} to
 * obtain the raw screenshot bytes using their specific browser API (e.g.,
 * Selenium WebDriver or Playwright). CSS selector-based element targeting,
 * when supported, should also be applied within that method.
 * </p>
 *
 * @param <T> the browser driver/page type used to capture the screenshot
 * @since 4.0.0
 */
@ToString
@EqualsAndHashCode
@Slf4j
public abstract class AbstractScreenshotHandler<T>
        implements Configurable<ScreenshotHandlerConfig> {

    @Getter
    private final ScreenshotHandlerConfig configuration =
            new ScreenshotHandlerConfig();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private final CachedStreamFactory streamFactory;

    protected AbstractScreenshotHandler() {
        this(null);
    }

    protected AbstractScreenshotHandler(CachedStreamFactory streamFactory) {
        this.streamFactory = Optional.ofNullable(
                streamFactory).orElseGet(CachedStreamFactory::new);
    }

    /**
     * Captures screenshot bytes for the given browser source. Implementations
     * are responsible for applying CSS selector-based element targeting (when
     * {@link ScreenshotHandlerConfig#getCssSelector()} is set) before
     * returning the final image bytes.
     *
     * @param source the browser driver or page object
     * @return screenshot bytes (PNG or JPEG)
     * @throws Exception if the screenshot cannot be captured
     */
    protected abstract byte[] captureScreenshotBytes(T source) throws Exception;

    /**
     * Takes a screenshot of the given browser source and stores it according
     * to the handler configuration. Exceptions during capture are logged but
     * not propagated.
     *
     * @param source the browser driver or page object
     * @param doc    the crawl document to attach the image to
     */
    public void takeScreenshot(T source, Doc doc) {
        var imageHandler = new DocImageHandler();
        imageHandler.setConfiguration(configuration);
        try {
            var bytes = captureScreenshotBytes(source);
            try (var in = streamFactory.newInputStream(
                    new ByteArrayInputStream(bytes))) {
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
