/* Copyright 2024 Norconex Inc.
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

import java.util.List;

import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

/**
 * Utilities to set browser options in additions to default ones, to optimize
 * testing.
 */
public final class WebDriverTestUtil {

    private WebDriverTestUtil() {
    }

    public static List<String> chromeTestArguments() {
        return List.of(
                "--headless=new",
                "--no-sandbox",
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--disable-blink-features=AutomationControlled",
                "--disable-extensions",
                "--disable-background-networking",
                "--disable-default-apps",
                "--disable-sync",
                "--disable-translate",
                "--metrics-recording-only",
                "--safebrowsing-disable-auto-update",
                "--disable-client-side-phishing-detection",
                "--disable-component-update",
                "--disable-domain-reliability",
                "--disable-features=OptimizationHints",
                "--disable-background-timer-throttling",
                "--disable-renderer-backgrounding",
                "--disable-ipc-flooding-protection",
                "--disable-sync-types",
                "--no-first-run",
                "--no-service-autorun",
                "--password-store=basic",
                "--use-mock-keychain",
                "--disable-gaia-services");
    }

    public static List<String> edgeTestArguments() {
        return List.of(
                "--headless=new",
                "--no-sandbox",
                "--disable-gpu",
                "--disable-dev-shm-usage",
                "--disable-blink-features=AutomationControlled");
    }

    public static List<String> firefoxTestArguments() {
        return List.of(
                "--headless",
                "--disable-gpu",
                "--disable-extensions",
                "--no-sandbox");
    }

    public static ChromeOptions chromeTestOptions() {
        var options = new ChromeOptions();
        options.addArguments(chromeTestArguments());
        return options;
    }

    public static EdgeOptions edgeTestOptions() {
        var options = new EdgeOptions();
        options.addArguments(edgeTestArguments());
        return options;
    }

    public static FirefoxOptions firefoxTestOptions() {
        var options = new FirefoxOptions();
        options.addArguments(firefoxTestArguments());
        // Disable Notifications and Popups
        options.addPreference("dom.webnotifications.enabled", false);
        options.addPreference(
                "permissions.default.desktop-notification", 2);
        options.addPreference("dom.push.enabled", false);
        // Disable Caching
        options.addPreference("browser.cache.disk.enable", false);
        options.addPreference("browser.cache.memory.enable", false);
        options.addPreference("network.http.use-cache", false);
        // Disable Telemetry and Reporting
        options.addPreference(
                "toolkit.telemetry.reportingpolicy.firstRun",
                false);
        options.addPreference(
                "datareporting.policy.dataSubmissionEnabled",
                false);
        options.addPreference("toolkit.telemetry.enabled", false);
        options.addPreference("toolkit.telemetry.archive.enabled",
                false);
        options.addPreference(
                "browser.newtabpage.activity-stream.feeds.telemetry",
                false);
        options.addPreference(
                "browser.newtabpage.activity-stream.telemetry",
                false);
        options.addPreference("browser.ping-centre.telemetry", false);
        options.addPreference("toolkit.telemetry.bhrPing.enabled",
                false);
        options.addPreference("toolkit.telemetry.unified", false);
        // Disable Plugins
        options.addPreference("plugin.state.flash", 0);
        options.addPreference("plugin.default.state", 0);
        // Block Updates
        options.addPreference("app.update.auto", false);
        options.addPreference("app.update.enabled", false);
        options.addPreference("browser.search.update", false);

        return options;
    }
}
