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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.openqa.selenium.remote.RemoteWebDriver;

@org.testcontainers.junit.jupiter.Testcontainers(disabledWithoutDocker = true)
@Timeout(300)
class WebDriverFactoryTest {

    @Test
    void testCreate() {
        var browserContainer =
                AbstractWebDriverHttpFetcherTest.createWebDriverContainer(
                        Browser.FIREFOX);
        browserContainer.start();
        try {
            var driver = WebDriverFactory.create(
                    new WebDriverFetcherConfig()
                            .setBrowser(Browser.FIREFOX)
                            .setRemoteURL(
                                    browserContainer.getSeleniumAddress()));
            try {
                assertThat(driver).isInstanceOf(RemoteWebDriver.class);
                var remoteDriver = (RemoteWebDriver) driver;
                assertThat(remoteDriver.getSessionId()).isNotNull();
                assertThat(remoteDriver.getCapabilities().getBrowserName())
                        .containsIgnoringCase("firefox");
            } finally {
                driver.quit();
            }
        } finally {
            browserContainer.stop();
        }
    }

}
