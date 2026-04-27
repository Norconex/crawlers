/* Copyright 2026 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;

import com.norconex.commons.lang.net.Host;

class WebDriverBrowserTest {

    // -------------------------------------------------------------------------
    // of(String)
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource(
        {
                "chrome,  CHROME",
                "CHROME,  CHROME",
                "Chrome,  CHROME",
                "firefox, FIREFOX",
                "FIREFOX, FIREFOX",
                "edge,    EDGE",
                "EDGE,    EDGE",
                "safari,  SAFARI",
                "opera,   OPERA",
                "custom,  CUSTOM"
        }
    )
    void testOfCaseInsensitive(String name, String expectedEnum) {
        assertThat(WebDriverBrowser.of(name))
                .isEqualTo(WebDriverBrowser.valueOf(expectedEnum));
    }

    @Test
    void testOfUnknownReturnsNull() {
        assertThat(WebDriverBrowser.of("unknown")).isNull();
    }

    @Test
    void testOfNullReturnsNull() {
        assertThat(WebDriverBrowser.of(null)).isNull();
    }

    // -------------------------------------------------------------------------
    // createOptions
    // -------------------------------------------------------------------------

    @Test
    void testCreateOptionsChromeReturnsNonNull() {
        var location = new WebDriverLocation(null, null, null);
        var options = WebDriverBrowser.CHROME.createOptions(location);
        assertThat(options).isNotNull().isInstanceOf(ChromeOptions.class);
    }

    @Test
    void testCreateOptionsFirefoxReturnsNonNull() {
        var location = new WebDriverLocation(null, null, null);
        var options = WebDriverBrowser.FIREFOX.createOptions(location);
        assertThat(options).isNotNull().isInstanceOf(FirefoxOptions.class);
    }

    @Test
    void testCreateOptionsEdgeReturnsNonNull() {
        var location = new WebDriverLocation(null, null, null);
        var options = WebDriverBrowser.EDGE.createOptions(location);
        assertThat(options).isNotNull().isInstanceOf(EdgeOptions.class);
    }

    @Test
    void testCreateOptionsSafariReturnsNonNull() {
        var location = new WebDriverLocation(null, null, null);
        var options = WebDriverBrowser.SAFARI.createOptions(location);
        assertThat(options).isNotNull();
    }

    @Test
    void testCreateOptionsOperaReturnsNonNull() {
        var location = new WebDriverLocation(null, null, null);
        var options = WebDriverBrowser.OPERA.createOptions(location);
        assertThat(options).isNotNull();
    }

    @Test
    void testCreateOptionsCustomReturnsNonNull() {
        var location = new WebDriverLocation(null, null, null);
        var options = WebDriverBrowser.CUSTOM.createOptions(location);
        assertThat(options)
                .isNotNull()
                .isInstanceOf(WebDriverBrowser.CustomDriverOptions.class);
    }

    // -------------------------------------------------------------------------
    // configureProxy
    // -------------------------------------------------------------------------

    @Test
    void testConfigureProxyChrome() {
        var location = new WebDriverLocation(null, null, null);
        var options = WebDriverBrowser.CHROME.createOptions(location);
        var host = new Host("proxy.example.com", 3128);
        assertThatCode(
                () -> WebDriverBrowser.CHROME.configureProxy(options, host))
                        .doesNotThrowAnyException();
    }

    @Test
    void testConfigureProxyFirefox() {
        var location = new WebDriverLocation(null, null, null);
        var options = WebDriverBrowser.FIREFOX.createOptions(location);
        var host = new Host("proxy.example.com", 3128);
        assertThatCode(
                () -> WebDriverBrowser.FIREFOX.configureProxy(options, host))
                        .doesNotThrowAnyException();
    }

    @Test
    void testConfigureProxyEdge() {
        var location = new WebDriverLocation(null, null, null);
        var options = WebDriverBrowser.EDGE.createOptions(location);
        var host = new Host("proxy.example.com", 3128);
        assertThatCode(
                () -> WebDriverBrowser.EDGE.configureProxy(options, host))
                        .doesNotThrowAnyException();
    }

    @Test
    void testConfigureProxyCustom() {
        var location = new WebDriverLocation(null, null, null);
        var options = WebDriverBrowser.CUSTOM.createOptions(location);
        var host = new Host("proxy.example.com", 3128);
        assertThatCode(
                () -> WebDriverBrowser.CUSTOM.configureProxy(options, host))
                        .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // getHtmlUnitBrowser
    // -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = { "CHROME", "FIREFOX", "EDGE" })
    void testGetHtmlUnitBrowserIsNonNullForSupportedBrowsers(String name) {
        var browser = WebDriverBrowser.valueOf(name);
        assertThat(browser.getHtmlUnitBrowser()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "SAFARI", "OPERA", "CUSTOM" })
    void testGetHtmlUnitBrowserIsNullForUnsupportedBrowsers(String name) {
        var browser = WebDriverBrowser.valueOf(name);
        assertThat(browser.getHtmlUnitBrowser()).isNull();
    }

    // -------------------------------------------------------------------------
    // CustomDriverOptions
    // -------------------------------------------------------------------------

    @Test
    void testCustomDriverOptionsGetExtraCapabilityAlwaysNull() {
        var options = new WebDriverBrowser.CustomDriverOptions();
        assertThat(options.getCapability("anything")).isNull();
    }

    @Test
    void testCustomDriverOptionsIsNotNull() {
        assertThat(new WebDriverBrowser.CustomDriverOptions()).isNotNull();
    }
}
