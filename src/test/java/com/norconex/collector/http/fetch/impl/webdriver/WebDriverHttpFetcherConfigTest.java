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

import java.awt.Dimension;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import com.norconex.collector.http.fetch.util.DocImageHandler.DirStructure;
import com.norconex.collector.http.fetch.util.DocImageHandler.Target;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;

class WebDriverHttpFetcherConfigTest  {

    @Test
    void testWriteReadFetcher() throws MalformedURLException {
        WebDriverHttpFetcher f = new WebDriverHttpFetcher();
        f.getRestrictions().add(new PropertyMatcher(
                TextMatcher.basic("field"),
                TextMatcher.basic("value")));

        ScreenshotHandler sh = new ScreenshotHandler();
        sh.setCssSelector("selector");
        sh.setImageFormat("gif");
        sh.setTargetDir(Paths.get("/target/dir"));
        sh.setTargetDirField("targetField");
        sh.setTargetDirStructure(DirStructure.DATE);
        sh.setTargetMetaField("targetMeta");
        sh.setTargets(Target.DIRECTORY, Target.METADATA);
        f.setScreenshotHandler(sh);

        XML.assertWriteRead(createFetcherConfig(), "fetcher");
    }


    @Test
    void testWriteReadConfig() throws MalformedURLException {
        XML.assertWriteRead(createFetcherConfig(), "fetcher");
    }

    private WebDriverHttpFetcherConfig createFetcherConfig()
            throws MalformedURLException {
        WebDriverHttpFetcherConfig c = new WebDriverHttpFetcherConfig();
        c.setBrowser(Browser.CHROME);
        c.setBrowserPath(Paths.get("/some/browser/path"));
        c.setDriverPath(Paths.get("/some/driver/path"));
        c.setRemoteURL(new URL("http://example.com"));
        c.setImplicitlyWait(4000);
        c.setInitScript("alert('hello init!');");
        c.setPageLoadTimeout(5000);
        c.setPageScript("alert('hello page!');");
        c.setScriptTimeout(6000);
        c.setWindowSize(new Dimension(666, 999));
        c.getCapabilities().setCapability("cap1", "val1");
        c.getCapabilities().setCapability("cap2", "val2");

        HttpSnifferConfig sc = new HttpSnifferConfig();
        sc.setPort(123);
        sc.setUserAgent("Agent 007");
        sc.getRequestHeaders().put("rh1", "hrval1");
        sc.getRequestHeaders().put("rh2", "hrval2");
        c.setHttpSnifferConfig(sc);

        return c;
    }
}
