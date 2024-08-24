/* Copyright 2020-2024 Norconex Inc.
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

import static com.norconex.commons.lang.config.Configurable.configure;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.awt.Dimension;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.web.fetch.impl.webdriver.WebDriverHttpFetcherConfig.WaitElementType;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.DirStructure;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.Target;

class WebDriverHttpFetcherConfigTest  {

    @Test
    void testWriteReadFetcher() throws MalformedURLException {

        var f = new WebDriverHttpFetcher();

        var c = f.getConfiguration();
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
        c.setCapabilities(MapUtil.toMap(
            "cap1", "val1",
            "cap2", "val2"
        ));

        var snif = new HttpSniffer();
        snif.getConfiguration()
            .setPort(123)
            .setUserAgent("Agent 007")
            .getRequestHeaders().putAll(MapUtil.toMap(
                    "rh1", "hrval1",
                    "rh2", "hrval2"));
        c.setHttpSniffer(snif);

        c.setReferenceFilters(List.of(
                configure(new GenericReferenceFilter(), cfg -> cfg
                        .setValueMatcher(TextMatcher.regex("test.*")))));

        var sh = new ScreenshotHandler();
        sh.getConfiguration()
            .setCssSelector("selector")
            .setImageFormat("gif")
            .setTargetDir(Paths.get("/target/dir"))
            .setTargetDirField("targetField")
            .setTargetDirStructure(DirStructure.DATE)
            .setTargetMetaField("targetMeta")
            .setTargets(List.of(Target.DIRECTORY, Target.METADATA));
        c.setScreenshotHandler(sh);

        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(f));
    }
}
