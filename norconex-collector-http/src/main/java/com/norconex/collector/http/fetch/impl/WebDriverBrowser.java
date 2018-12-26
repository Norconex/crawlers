/* Copyright 2018 Norconex Inc.
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
package com.norconex.collector.http.fetch.impl;

import java.nio.file.Path;

import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriverService;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.opera.OperaDriverService;
import org.openqa.selenium.opera.OperaOptions;
import org.openqa.selenium.remote.service.DriverService.Builder;
import org.openqa.selenium.safari.SafariDriverService;
import org.openqa.selenium.safari.SafariOptions;

/**
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public enum WebDriverBrowser {
    CHROME() {
        @Override
        public MutableCapabilities createCapabilities(Path browserPath) {
            ChromeOptions options = new ChromeOptions().setHeadless(true);
            if (browserPath != null) {
                options.setBinary(browserPath.toFile());
            }
            return options;
        }
        @Override
        public Builder<?, ?> createServiceBuilder() {
            return new ChromeDriverService.Builder();
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
        public MutableCapabilities createCapabilities(Path browserPath) {
            FirefoxOptions options = new FirefoxOptions().setHeadless(true);
            if (browserPath != null) {
                options.setBinary(browserPath);
            }
            //TODO consider making page load strategy configurable (with
            //different defaults).
            options.setPageLoadStrategy(PageLoadStrategy.EAGER);
            return options;
        }
        @Override
        public Builder<?, ?> createServiceBuilder() {
            return new GeckoDriverService.Builder();
        }
    },
    EDGE() {
        @Override
        public MutableCapabilities createCapabilities(Path browserPath) {
            return new EdgeOptions();
        }
        @Override
        public Builder<?, ?> createServiceBuilder() {
            return new EdgeDriverService.Builder();
        }
    },
    SAFARI() {
        @Override
        public MutableCapabilities createCapabilities(Path browserPath) {
            return new SafariOptions();
        }
        @Override
        public Builder<?, ?> createServiceBuilder() {
            return new SafariDriverService.Builder();
        }
    },
    OPERA() {
        @Override
        public MutableCapabilities createCapabilities(Path browserPath) {
            return new OperaOptions();
        }
        @Override
        public Builder<?, ?> createServiceBuilder() {
            return new OperaDriverService.Builder();
        }
    },
    /*
    HTMLUNIT
    PHANTOMJS,
    ANDROID,
    IOS*/
    ;

    public abstract MutableCapabilities createCapabilities(Path browserPath);
    public abstract Builder<?, ?> createServiceBuilder();
    public static WebDriverBrowser from(String name) {
        for (WebDriverBrowser d : WebDriverBrowser.values()) {
            if (d.name().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }
}