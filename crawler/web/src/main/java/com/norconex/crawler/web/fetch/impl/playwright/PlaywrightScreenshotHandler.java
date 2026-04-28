/* Copyright 2024-2026 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl.playwright;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.playwright.Page;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.web.fetch.util.AbstractScreenshotHandler;
import com.norconex.crawler.web.fetch.util.ScreenshotHandlerConfig;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Takes screenshots of pages using a Playwright {@link Page}.
 * Either the entire page viewport, or a specific DOM element identified by
 * a CSS selector. Screenshot images can be stored in a document
 * metadata/field or in a local directory.
 * </p>
 *
 * <p>
 * Unlike the Selenium-based {@code ScreenshotHandler}, Playwright
 * captures element screenshots natively without manual image cropping.
 * </p>
 *
 * @see PlaywrightFetcher
 * @see ScreenshotHandlerConfig
 * @since 4.0.0
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PlaywrightScreenshotHandler
        extends AbstractScreenshotHandler<Page> {

    public PlaywrightScreenshotHandler() {
        super();
    }

    public PlaywrightScreenshotHandler(CachedStreamFactory streamFactory) {
        super(streamFactory);
    }

    /**
     * Captures a screenshot using Playwright's native API. When a CSS
     * selector is configured, only the matching element is captured (no manual
     * image cropping required); otherwise the full viewport is captured.
     *
     * @param page the Playwright page to screenshot
     * @return screenshot bytes
     */
    @Override
    protected byte[] captureScreenshotBytes(Page page) {
        // Playwright natively captures just the matching element when a
        // CSS selector is provided — no manual cropping required.
        if (StringUtils.isNotBlank(getConfiguration().getCssSelector())) {
            return page.locator(getConfiguration().getCssSelector())
                    .screenshot();
        }
        return page.screenshot();
    }
}
