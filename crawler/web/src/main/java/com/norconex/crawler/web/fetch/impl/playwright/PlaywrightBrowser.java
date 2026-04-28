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

import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

/**
 * Playwright-supported browser engines.
 * @since 4.0.0
 */
public enum PlaywrightBrowser {
    /**
     * Chromium-based browser (also used by Google Chrome and Microsoft Edge).
     */
    CHROMIUM {
        @Override
        public BrowserType browserType(Playwright playwright) {
            return playwright.chromium();
        }
    },
    /**
     * Mozilla Firefox.
     */
    FIREFOX {
        @Override
        public BrowserType browserType(Playwright playwright) {
            return playwright.firefox();
        }
    },
    /**
     * WebKit-based browser (also used by Apple Safari).
     */
    WEBKIT {
        @Override
        public BrowserType browserType(Playwright playwright) {
            return playwright.webkit();
        }
    };

    /**
     * Returns the Playwright {@link BrowserType} for this engine.
     * @param playwright the Playwright instance
     * @return the browser type
     */
    public abstract BrowserType browserType(Playwright playwright);
}
