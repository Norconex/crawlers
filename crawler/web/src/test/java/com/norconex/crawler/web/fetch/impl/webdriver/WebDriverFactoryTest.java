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
package com.norconex.crawler.web.fetch.impl.webdriver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(300)
class WebDriverFactoryTest {

    @Test
    void testCreate() throws Exception {
        Assumptions.assumeTrue(isLocalFirefoxAvailable(),
                "Local Firefox not detected.");

        var config = new WebDriverFetcherConfig();
        config.setBrowser(WebDriverBrowser.FIREFOX);
        try (var session = WebDriverFactory.createSession(config)) {
            var driver = session.driver();
            assertThat(driver).isNotNull();
            assertThat(driver.getClass().getSimpleName())
                    .containsIgnoringCase("firefox");
        }
    }

    private boolean isLocalFirefoxAvailable() {
        return AbstractWebDriverHttpFetcherTest.isLocalBrowserDetectable(
                WebDriverBrowser.FIREFOX);
    }
}
