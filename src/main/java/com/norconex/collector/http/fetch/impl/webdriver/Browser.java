/* Copyright 2020 Norconex Inc.
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

import java.nio.file.Path;
import java.util.function.Supplier;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeDriverService;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.opera.OperaDriver;
import org.openqa.selenium.opera.OperaDriverService;
import org.openqa.selenium.opera.OperaOptions;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

/**
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public enum Browser {

    CHROME() {
        @Override
        public MutableCapabilities capabilities(Path browserPath) {
            ChromeOptions options = new ChromeOptions().setHeadless(true);
            if (browserPath != null) {
                options.setBinary(browserPath.toFile());
            }
            return options;
        }
        @Override
        public synchronized WebDriver driver(
                Path driverPath, MutableCapabilities caps) {
            return webDriver(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY,
                    driverPath, () -> new ChromeDriver((ChromeOptions) caps));
        }
    },
    FIREFOX() {
        /* Firefox (remote) driver seems to fail to return when page load
         * strategy is not set to EAGER.  The options are:
         *
         * NORMAL: Waits for pages to load and ready state to be 'complete'.
         *
         * EAGER:  Waits for pages to load and for ready state to be
         *         'interactive' or 'complete'.
         *
         * NONE:   Does not wait for pages to load, returning immediately.
         */
        @Override
        public MutableCapabilities capabilities(Path browserPath) {
            FirefoxOptions options = new FirefoxOptions();
            options.setHeadless(true);
            if (browserPath != null) {
                options.setBinary(browserPath);

            }
            //TODO consider making page load strategy configurable (with
            //different defaults).
            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            return options;
        }
        @Override
        public synchronized WebDriver driver(
                Path driverPath, MutableCapabilities caps) {
            return webDriver(GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY,
                    driverPath, () -> new FirefoxDriver((FirefoxOptions) caps));
        }
    },
    EDGE() {
        @Override
        public MutableCapabilities capabilities(Path browserPath) {
            return new EdgeOptions();
        }
        @Override
        public synchronized WebDriver driver(
                Path driverPath, MutableCapabilities caps) {
            return webDriver(EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY,
                    driverPath, () -> new EdgeDriver((EdgeOptions) caps));
        }
    },
    SAFARI() {
        @Override
        public MutableCapabilities capabilities(Path browserPath) {
            return new SafariOptions();
        }
        @Override
        public synchronized WebDriver driver(
                Path driverPath, MutableCapabilities caps) {
            // Safari path is constant so it is ignored if specified.
            return new SafariDriver((SafariOptions) caps);
        }
    },
    OPERA() {
        @Override
        public MutableCapabilities capabilities(Path browserPath) {
            return new OperaOptions();
        }
        @Override
        public synchronized WebDriver driver(
                Path driverPath, MutableCapabilities caps) {
            return webDriver(OperaDriverService.OPERA_DRIVER_EXE_PROPERTY,
                    driverPath, () -> new OperaDriver((OperaOptions) caps));
        }
    },
    /*
    HTMLUNIT
    PHANTOMJS,
    ANDROID,
    IOS*/
    ;

    public abstract MutableCapabilities capabilities(Path browserPath);
    public abstract WebDriver driver(
            Path driverPath, MutableCapabilities capabilities);
    public static Browser from(String name) {
        for (Browser d : Browser.values()) {
            if (d.name().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }

    // Set system property but restore to whatever value
    // in case other crawlers have a different value.
    // This is why we have it synchronized too.
    private static synchronized WebDriver webDriver(
            String sysPropertyKey, Path driverPath, Supplier<WebDriver> s) {
        String orig = System.getProperty(sysPropertyKey);
        if (driverPath != null) {
            System.setProperty(
                    sysPropertyKey, driverPath.toAbsolutePath().toString());
        }
        WebDriver driver = s.get();
        if (driverPath != null) {
            System.clearProperty(sysPropertyKey);
            if (orig != null) {
                System.setProperty(sysPropertyKey, orig);
            }
        }
        return driver;
    }

}