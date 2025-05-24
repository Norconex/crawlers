/* Copyright 2025 Norconex Inc.
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

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.remote.CapabilityType;

import com.norconex.crawler.core.CrawlerException;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class WebDriverFactory {

    public static WebDriver create(WebDriverFetcherConfig config) {
        var browser = config.getBrowser();
        LOG.info("Creating {} web driver.", browser);

        var location = new WebDriverLocation(
                config.getDriverPath(),
                config.getBrowserPath(),
                config.getRemoteURL());

        //--- Options ---
        var options = browser.createOptions(location);
        if (config.getHttpSniffer() != null) {
            LOG.info("Starting {} HTTP sniffing proxy...", browser);
            var httpSniffer = config.getHttpSniffer();
            httpSniffer.configureBrowser(browser, options);
        }
        options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
        for (var en : config.getCapabilities().entrySet()) {
            options.setCapability(en.getKey(), en.getValue());
        }
        // @formatter:off
        // add arguments to drivers supporting it
        if (options instanceof FirefoxOptions fireOpts) {
            fireOpts.addArguments(config.getArguments());
        }
        if (options instanceof ChromiumOptions<?> chromiumOpts) {
            // covers chrome, edge, and possibly others
            chromiumOpts.addArguments(config.getArguments());
        }
        // @formatter:on

        //--- Web Driver ---
        WebDriver driver = null;
        if (config.isUseHtmlUnit()) {
            var v = browser.getHtmlUnitBrowser();
            if (v == null) {
                throw new CrawlerException(
                        "Unsupported HtmlUnit browser version: " + v);
            }
            driver = new HtmlUnitDriver(v, true);
        } else {
            driver = browser.createDriver(location, options);
        }
        return driver;
    }
}
