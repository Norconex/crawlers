/* Copyright 2021-2023 Norconex Inc.
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

import org.apache.commons.lang3.mutable.MutableObject;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.support.ThreadGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.web.fetch.impl.webdriver.Browser.WebDriverSupplier;

/**
 * <p>
 * Creates and holds web drivers, keeping and returning only 1 instance per
 * thread.
 * </p>
 *
 * @since 3.0.0
 */
class WebDriverHolder {

    private static final Logger LOG = LoggerFactory.getLogger(
            WebDriverHolder.class);

    private final WebDriverSupplier driverSupplier;
    private final MutableObject<MutableCapabilities> options =
            new MutableObject<>();
    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();

    public WebDriverHolder(WebDriverHttpFetcherConfig cfg) {
        driverSupplier = cfg.getBrowser().driverSupplier(
                new WebDriverLocation(
                        cfg.getDriverPath(),
                        cfg.getBrowserPath(),
                        cfg.getRemoteURL()),
                o -> {
//                    configureWebDriverLogging(o);
//                    o.setCapability(CapabilityType.ACCEPT_SSL_CERTS, true);
                    o.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
                    o.merge(cfg.getCapabilities());
                    options.setValue(o);
                }
        );
    }

    public MutableObject<MutableCapabilities> getDriverOptions() {
        return options;
    }

    public WebDriver getDriver() {
       var driver = DRIVER.get();
       if (driver == null) {
           DRIVER.set(ThreadGuard.protect(driverSupplier.get()));
       }
       return DRIVER.get();
    }

    public void releaseDriver() {
        options.setValue(null);
        var driver = DRIVER.get();
        if (driver != null) {
            driver.quit();
            DRIVER.remove();
        }
    }

//    private static void configureWebDriverLogging(
//            MutableCapabilities capabilities) {
//        var logPrefs = new LoggingPreferences();
//        var level = SLF4JUtil.toJavaLevel(SLF4JUtil.getLevel(LOG));
//        logPrefs.enable(LogType.PERFORMANCE, level);
//        logPrefs.enable(LogType.PROFILER, level);
//        logPrefs.enable(LogType.BROWSER, level);
//        logPrefs.enable(LogType.CLIENT, level);
//        logPrefs.enable(LogType.DRIVER, level);
//        logPrefs.enable(LogType.SERVER, level);
//        capabilities.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);
//    }
}
