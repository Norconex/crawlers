/* Copyright 2020-2023 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.awt.Dimension;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.web.fetch.impl.webdriver.WebDriverHttpFetcherConfig.WaitElementType;
import com.norconex.crawler.web.fetch.util.DocImageHandler.DirStructure;
import com.norconex.crawler.web.fetch.util.DocImageHandler.Target;

class WebDriverHttpFetcherConfigTest  {

    @Test
    void testWriteReadFetcher() throws MalformedURLException {

        var c = new WebDriverHttpFetcherConfig();
        c.setBrowser(Browser.CHROME);
        c.setBrowserPath(Paths.get("/some/browser/path"));
        c.setDriverPath(Paths.get("/some/driver/path"));
        c.setRemoteURL(new URL("http://example.com"));
        c.setImplicitlyWait(4000);
        c.setEarlyPageScript("alert('hello init!');");
        c.setPageLoadTimeout(5000);
        c.setLatePageScript("alert('hello page!');");
        c.setScriptTimeout(6000);
        c.setWaitForElementSelector("#header");
        c.setWaitForElementTimeout(1234);
        c.setWaitForElementType(WaitElementType.ID);
        c.setWindowSize(new Dimension(666, 999));
        c.getCapabilities().setCapability("cap1", "val1");
        c.getCapabilities().setCapability("cap2", "val2");

        var sc = new HttpSnifferConfig();
        sc.setPort(123);
        sc.setUserAgent("Agent 007");
        sc.getRequestHeaders().put("rh1", "hrval1");
        sc.getRequestHeaders().put("rh2", "hrval2");
        c.setHttpSnifferConfig(sc);

        var f = new WebDriverHttpFetcher(c);
        f.setReferenceFilters(List.of(new GenericReferenceFilter(
                TextMatcher.regex("test.*"))));

        var sh = new ScreenshotHandler();
        sh.setCssSelector("selector");
        sh.setImageFormat("gif");
        sh.setTargetDir(Paths.get("/target/dir"));
        sh.setTargetDirField("targetField");
        sh.setTargetDirStructure(DirStructure.DATE);
        sh.setTargetMetaField("targetMeta");
        sh.setTargets(List.of(Target.DIRECTORY, Target.METADATA));
        f.setScreenshotHandler(sh);

        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(f, "fetcher"));
    }
}
