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

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.openqa.selenium.chrome.ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY;
import static org.openqa.selenium.edge.EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY;
import static org.openqa.selenium.firefox.GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.commons.lang.SystemUtil;

/**
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public enum Browser {

    CHROME() {
        @Override
        WebDriverSupplier driverSupplier(
                WebDriverLocation location,
                Consumer<MutableCapabilities> optionsConsumer) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            ofNullable(location.getBrowserPath()).ifPresent(
                    p -> options.setBinary(p.toFile()));
            optionsConsumer.accept(options);
            return new WebDriverSupplier(new WebDriverBuilder()
                .driverClass(ChromeDriver.class)
                .driverSystemProperty(CHROME_DRIVER_EXE_PROPERTY)
                .location(location)
                .options(options));
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
        WebDriverSupplier driverSupplier(
                WebDriverLocation location,
                Consumer<MutableCapabilities> optionsConsumer) {
            FirefoxOptions options = new FirefoxOptions();
            options.addArguments("-headless");
            ofNullable(location.getBrowserPath()).ifPresent(options::setBinary);
            //TODO consider making page load strategy configurable (with
            //different defaults).
            options.setPageLoadStrategy(PageLoadStrategy.EAGER);

            FirefoxProfile profile = options.getProfile();
            profile.setAcceptUntrustedCertificates(true);
            profile.setAssumeUntrustedCertificateIssuer(false);
            profile.setPreference("devtools.console.stdout.content", true);
            profile.setPreference("browser.privatebrowsing.autostart", true);
            profile.setPreference("browser.aboutHomeSnippets.updateUrl", "");
            profile.setPreference("services.settings.server", EMPTY);
            profile.setPreference("location.services.mozilla.com", EMPTY);
            profile.setPreference("shavar.services.mozilla.com", EMPTY);
            profile.setPreference("app.normandy.enabled", false);
            profile.setPreference("app.update.service.enabled", false);
            profile.setPreference("app.update.staging.enabled", false);

            options.setProfile(profile);
            optionsConsumer.accept(options);
            return new WebDriverSupplier(new WebDriverBuilder()
                    .driverClass(FirefoxDriver.class)
                    .driverSystemProperty(GECKO_DRIVER_EXE_PROPERTY)
                    .location(location)
                    .options(options));
        }
    },
    EDGE() {
        @Override
        WebDriverSupplier driverSupplier(
                WebDriverLocation location,
                Consumer<MutableCapabilities> optionsConsumer) {
            EdgeOptions options = new EdgeOptions();
            optionsConsumer.accept(options);
            return new WebDriverSupplier(new WebDriverBuilder()
                    .driverClass(EdgeDriver.class)
                    .driverSystemProperty(EDGE_DRIVER_EXE_PROPERTY)
                    .location(location)
                    .options(options));
        }
    },
    SAFARI() {
        @Override
        WebDriverSupplier driverSupplier(
                WebDriverLocation location,
                Consumer<MutableCapabilities> optionsConsumer) {
            SafariOptions options = new SafariOptions();
            // Safari path is constant so it is ignored if specified.
            optionsConsumer.accept(options);
            return new WebDriverSupplier(new WebDriverBuilder()
                    .driverClass(SafariDriver.class)
                    .location(location)
                    .options(options));
        }
    },
    OPERA() {
        @Override
        WebDriverSupplier driverSupplier(
                WebDriverLocation location,
                Consumer<MutableCapabilities> optionsConsumer) {
            ChromeOptions options = new ChromeOptions();
            options.setExperimentalOption("w3c", true);
            options.addArguments("--headless=new");
            optionsConsumer.accept(options);
            return new WebDriverSupplier(new WebDriverBuilder()
                    .driverClass(ChromeDriver.class)
                    .driverSystemProperty(OPERA_DRIVER_EXE_PROPERTY)
                    .location(location)
                    .options(options));
        }
    },
    /*
    HTMLUNIT
    PHANTOMJS,
    ANDROID,
    IOS*/
    ;

    private static final Logger LOG = LoggerFactory.getLogger(Browser.class);
    private static final String OPERA_DRIVER_EXE_PROPERTY =
            "webdriver.opera.driver";

    abstract WebDriverSupplier driverSupplier(
            WebDriverLocation driverLocation,
            Consumer<MutableCapabilities> optionsConsumer);


    public static Browser of(String name) {
        for (Browser d : Browser.values()) {
            if (d.name().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }

    static class WebDriverSupplier implements Supplier<WebDriver> {
        private final WebDriverBuilder builder;
        public WebDriverSupplier(WebDriverBuilder builder) {
            this.builder = Objects.requireNonNull(builder);
        }
        @Override
        public WebDriver get() {
            return builder.build();
        }
    }

    private static class WebDriverBuilder {
        private WebDriverLocation location;
        private String driverSystemProperty;
        private MutableCapabilities options;
        private Class<? extends WebDriver> driverClass;
        WebDriverBuilder location(WebDriverLocation location) {
            this.location = location;
            return this;
        }
        WebDriverBuilder driverSystemProperty(String propertyName) {
            driverSystemProperty = propertyName;
            return this;
        }
        WebDriverBuilder options(MutableCapabilities options) {
            this.options = options;
            return this;
        }
        WebDriverBuilder driverClass(Class<? extends WebDriver> driverClass) {
            this.driverClass = driverClass;
            return this;
        }

        // Set system property but restore to whatever value
        // in case other crawlers have a different value.
        // This is why we have it synchronized too.
        synchronized WebDriver build() {
            Objects.requireNonNull(location);
            Objects.requireNonNull(options);
            Objects.requireNonNull(driverClass);

            String driverPath = location.getDriverPath() != null
                    ? location.getDriverPath().toAbsolutePath().toString()
                    : null;
            try {
                return SystemUtil.callWithProperty(
                        driverSystemProperty, driverPath, () -> {
                    if (location.getRemoteURL() != null) {
                        LOG.info("Creating remote \"{}\" web driver.",
                                driverClass.getSimpleName());
                        return new RemoteWebDriver(
                                location.getRemoteURL(), options);
                    }
                    LOG.info("Creating local \"{}\" web driver.",
                            driverClass.getSimpleName());
                    return ConstructorUtils.invokeExactConstructor(
                            driverClass, options);
                });
            } catch (Exception e) {
                throw new CollectorException("Could not build web driver", e);
            }
        }
    }
}