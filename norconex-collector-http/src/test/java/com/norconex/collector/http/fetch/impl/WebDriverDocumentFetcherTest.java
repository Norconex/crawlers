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

import java.io.IOException;

import org.junit.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.Sleeper;

//TODO merge http client with document fetcher.
// have 1 doc fetcher and 1 http fetcher that can be the same or different.
// have ability to specify different fetchers for different URL patterns.

public class WebDriverDocumentFetcherTest  {

    private static final Logger LOG =
            LoggerFactory.getLogger(WebDriverDocumentFetcherTest.class);

    public static final String CHROME_DRIVER_PATH =
            "C:\\Apps\\chromedriver\\2.42\\chromedriver.exe";


    //TODO Make it a skip test

    @Test
    public void testChromeDriver() throws IOException {
        // Optional, if not specified, WebDriver will search your path for chromedriver.
        System.setProperty("webdriver.chrome.driver", CHROME_DRIVER_PATH);
        ChromeOptions options = new ChromeOptions();
        options.setHeadless(true);
        WebDriver driver = new ChromeDriver(options);
        driver.get("http://127.0.0.1/");
//        driver.get("http://www.google.com?q=test");
        Sleeper.sleepSeconds(5); // Let the user actually see something!
//        WebElement searchBox = driver.findElement(By.name("q"));
//        searchBox.sendKeys("ChromeDriver");
//        searchBox.submit();
//        Thread.sleep(5000);  // Let the user actually see something!

        System.out.println("SOURCE:\n" + driver.getPageSource());


        driver.quit();

    }

}
