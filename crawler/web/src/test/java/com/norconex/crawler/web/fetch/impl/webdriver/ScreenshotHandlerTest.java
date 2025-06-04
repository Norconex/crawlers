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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.OutputType;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.DirStructure;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.Target;
import com.norconex.crawler.web.mocks.MockWebDriver;
import com.norconex.importer.doc.Doc;

class ScreenshotHandlerTest {

    @Test
    void testWriteRead() {
        var h = new ScreenshotHandler();
        h.getConfiguration()
                .setCssSelector("body .content")
                .setImageFormat("jpg")
                .setTargetDir(Paths.get("/tmp/blah"))
                .setTargetDirStructure(DirStructure.URL2PATH)
                .setTargetDirField("docImage")
                .setTargetMetaField("docMeta")
                .setTargets(List.of(Target.DIRECTORY, Target.METADATA));

        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(h));
    }

    @Test
    void testExceptionSwallow() {
        var driver = mock(MockWebDriver.class);
        when(driver.getScreenshotAs(OutputType.BYTES))
                .thenThrow(UnsupportedOperationException.class);
        var doc = mock(Doc.class);
        var h = spy(new ScreenshotHandler());
        assertThatNoException().isThrownBy(() -> {
            h.takeScreenshot(driver, doc);
        });
        verify(h, times(0)).getConfiguration();

    }
}
