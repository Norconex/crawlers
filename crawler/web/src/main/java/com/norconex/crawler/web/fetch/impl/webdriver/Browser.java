/* Copyright 2020-2025 Norconex Inc.
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

import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.htmlunit.BrowserVersion;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.Proxy;
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
import org.openqa.selenium.internal.Require;
import org.openqa.selenium.remote.AbstractDriverOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.net.Host;
import com.norconex.crawler.core.CrawlerException;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * A web browser. Encapsulates browser-specific capabilities and driver
 * creation.
 * @since 3.0.0
 */
public enum Browser {

    /* NOTE: As per #844, we added --no-sandbox to the chrome driver, but it
     * started leaking orphan chrome processes so we are taking it out
     * by default to prevent the issue and we make it configurable instead.
     */
    CHROME(
            // browser-specific options
            location -> {
                var options = new ChromeOptions();
                options.addArguments("--headless=new");
                ofNullable(location.getBrowserPath())
                        .ifPresent(p -> options.setBinary(p.toFile()));
                return options;
            },
            // web driver factory
            (location, options) -> new WebDriverBuilder()
                    .driverClass(ChromeDriver.class)
                    .driverSystemProperty(
                            ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY)
                    .location(location)
                    .options(options)
                    .build(),
            // proxy setup
            (options, host) -> {
                var chromeOptions = (ChromeOptions) options;
                chromeOptions.addArguments(Tools.chromiumCommonArgs(host));
                if (Tools.LOG.isDebugEnabled()) {
                    System.setProperty(
                            "webdriver.chrome.verboseLogging", "true");
                }
            },
            BrowserVersion.CHROME),

    /* NOTE: Firefox (remote) driver seems to fail to return when page load
     * strategy is not set to EAGER.  The options are:
     *
     * NORMAL: Waits for pages to load and ready state to be 'complete'.
     *
     * EAGER:  Waits for pages to load and for ready state to be
     *         'interactive' or 'complete'.
     *
     * NONE:   Does not wait for pages to load, returning immediately.
     */
    FIREFOX(
            // browser-specific options
            location -> {
                var options = new FirefoxOptions();
                options.addArguments("-headless");
                ofNullable(location.getBrowserPath())
                        .ifPresent(options::setBinary);
                //TODO consider making page load strategy configurable (with
                //different defaults).
                options.setPageLoadStrategy(PageLoadStrategy.EAGER);

                var profile = options.getProfile();
                profile.setAcceptUntrustedCertificates(true);
                profile.setAssumeUntrustedCertificateIssuer(false);
                profile.setPreference("devtools.console.stdout.content", true);
                profile.setPreference(
                        "browser.privatebrowsing.autostart",
                        true);
                profile.setPreference(
                        "browser.aboutHomeSnippets.updateUrl",
                        "");
                profile.setPreference("services.settings.server", EMPTY);
                profile.setPreference("location.services.mozilla.com", EMPTY);
                profile.setPreference("shavar.services.mozilla.com", EMPTY);
                profile.setPreference("app.normandy.enabled", false);
                profile.setPreference("app.update.service.enabled", false);
                profile.setPreference("app.update.staging.enabled", false);
                options.setProfile(profile);
                return options;
            },
            // web driver factory
            (location, options) -> new WebDriverBuilder()
                    .driverClass(FirefoxDriver.class)
                    .driverSystemProperty(
                            GeckoDriverService.GECKO_DRIVER_EXE_PROPERTY)
                    .location(location)
                    .options(options)
                    .build(),
            // proxy setup
            (options, host) -> {
                // Fix bug with firefox where request/response filters are not
                // triggered properly unless dealing with firefox profile
                var profile = ((FirefoxOptions) options).getProfile();
                profile.setAcceptUntrustedCertificates(true);
                profile.setAssumeUntrustedCertificateIssuer(true);
                profile.setPreference("network.proxy.http", host.getName());
                profile.setPreference("network.proxy.http_port",
                        host.getPort());
                profile.setPreference("network.proxy.ssl", host.getName());
                profile.setPreference("network.proxy.ssl_port", host.getPort());
                profile.setPreference("network.proxy.type", 1);
                profile.setPreference("network.proxy.no_proxies_on", "");
                profile.setPreference("devtools.console.stdout.content", true);
                // Required since FF v67 to enable a localhost proxy:
                // https://bugzilla.mozilla.org/show_bug.cgi?id=1535581
                profile.setPreference(
                        "network.proxy.allow_hijacking_localhost", true);
                ((FirefoxOptions) options).setProfile(profile);
            },
            BrowserVersion.FIREFOX),

    EDGE(
            // browser-specific options
            location -> new EdgeOptions(),
            // web driver factory
            (location, options) -> new WebDriverBuilder()
                    .driverClass(EdgeDriver.class)
                    .driverSystemProperty(
                            EdgeDriverService.EDGE_DRIVER_EXE_PROPERTY)
                    .location(location)
                    .options(options)
                    .build(),
            // proxy setup
            (options, host) -> {
                ((EdgeOptions) options).setProxy(
                        new Proxy().setHttpProxy(host.toString()));
            },
            BrowserVersion.EDGE),

    // NOTE: Safari path is constant so it is ignored if supplied.
    SAFARI(
            // browser-specific options
            location -> new SafariOptions(),
            // web driver factory
            (location, options) -> new WebDriverBuilder()
                    .driverClass(SafariDriver.class)
                    .location(location)
                    .options(options)
                    .build(),
            // proxy setup
            (options, host) -> {
                ((SafariOptions) options).setProxy(
                        new Proxy().setHttpProxy(host.toString()));
            },
            null),

    OPERA(
            // browser-specific options
            location -> new ChromeOptions()
                    .setExperimentalOption("w3c", true)
                    .addArguments("--headless=new"),
            // web driver factory
            (location, options) -> new WebDriverBuilder()
                    .driverClass(ChromeDriver.class)
                    .driverSystemProperty(Browser.OPERA_DRIVER_EXE_PROPERTY)
                    .location(location)
                    .options(options)
                    .build(),
            // proxy setup
            (options, host) -> {
                ((ChromeOptions) options)
                        .addArguments("--user-agent=Opera/9.80 "
                                + "(Windows NT 10.0; Win64; x64)")
                        .addArguments(Tools.chromiumCommonArgs(host));
            },
            null),

    CUSTOM(
            // browser-specific options
            location -> new CustomDriverOptions(),
            // web driver factory
            (location, options) -> new WebDriverBuilder()
                    .location(location)
                    .options(options)
                    .build(),
            // proxy setup
            (options, host) -> options.setCapability(
                    CapabilityType.PROXY,
                    Require.nonNull(
                            "Proxy",
                            new Proxy().setHttpProxy(host.toString()))),
            null);

    private static final Logger LOG = Tools.LOG;
    private static final String OPERA_DRIVER_EXE_PROPERTY =
            "webdriver.opera.driver";

    private final Function<WebDriverLocation,
            MutableCapabilities> optionsSupplier;
    private final BiFunction<WebDriverLocation, MutableCapabilities,
            WebDriver> driverFactory;
    private final BiConsumer<MutableCapabilities, Host> proxySetter;

    @Getter(value = AccessLevel.PACKAGE)
    private final BrowserVersion htmlUnitBrowser;

    Browser(
            Function<WebDriverLocation, MutableCapabilities> optionsSupplier,
            BiFunction<WebDriverLocation, MutableCapabilities,
                    WebDriver> driverFactory,
            BiConsumer<MutableCapabilities, Host> proxySetter,
            BrowserVersion htmlUnitBrowser) {
        this.optionsSupplier = optionsSupplier;
        this.driverFactory = driverFactory;
        this.proxySetter = proxySetter;
        this.htmlUnitBrowser = htmlUnitBrowser;
    }

    public MutableCapabilities createOptions(WebDriverLocation location) {
        return optionsSupplier.apply(location);
    }

    public WebDriver createDriver(
            WebDriverLocation location,
            MutableCapabilities options) {
        return driverFactory.apply(location, options);
    }

    public void configureProxy(MutableCapabilities options, Host proxyHost) {
        proxySetter.accept(options, proxyHost);
    }

    public static Browser of(String name) {
        for (Browser d : Browser.values()) {
            if (d.name().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
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

            var driverPath = location.getDriverPath() != null
                    ? location.getDriverPath().toAbsolutePath().toString()
                    : null;
            try {
                return SystemUtil.callWithProperty(
                        driverSystemProperty, driverPath, () -> {
                            if (location.getRemoteURL() != null) {
                                LOG.info("Creating remote \"{}\" web driver.",
                                        driverClass.getSimpleName());
                                var rwd = new RemoteWebDriver(
                                        location.getRemoteURL(),
                                        options, false);
                                LOG.info("Remote \"{}\" web driver created.",
                                        driverClass.getSimpleName());
                                return rwd;
                            }
                            LOG.info("Creating local \"{}\" web driver.",
                                    driverClass.getSimpleName());
                            var constructor = ConstructorUtils
                                    .getMatchingAccessibleConstructor(
                                            driverClass, options.getClass());
                            if (constructor != null) {
                                return constructor.newInstance(options);
                            }
                            return driverClass.getDeclaredConstructor()
                                    .newInstance();
                        });
            } catch (Exception e) {
                throw new CrawlerException("Could not build web driver", e);
            }
        }
    }

    public static class CustomDriverOptions
            extends AbstractDriverOptions<AbstractDriverOptions<?>> {
        private static final long serialVersionUID = 1L;

        @Override
        protected Object getExtraCapability(String capabilityName) {
            return null;
        }

        @Override
        protected Set<String> getExtraCapabilityNames() {
            return Collections.emptySet();
        }
    }

    private static final class Tools {
        private static final Logger LOG = LoggerFactory.getLogger(
                Browser.class); //NOSONAR

        private Tools() {
        }

        private static String[] chromiumCommonArgs(Host host) {
            // Some arguments are required since Chrome v72 to enable a
            // localhost proxy:
            // https://bugs.chromium.org/p/chromium/issues/detail?id=899126#c15
            return new String[] {
                    "--proxy-bypass-list=<-loopback>",
                    "--proxy-server=" + host,
                    "--disable-popup-blocking",
                    "--disable-extensions",
                    "--disable-dev-shm-usage",
                    "--disable-gpu",
                    "--disable-software-rasterizer",
                    "--disable-infobars",
                    "--disable-browser-side-navigation",
                    "--disable-features=EnableEphemeralFlashPermission",
                    "--disable-translate",
                    "--disable-sync",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-sign-in"
            };
        }
    }
}
