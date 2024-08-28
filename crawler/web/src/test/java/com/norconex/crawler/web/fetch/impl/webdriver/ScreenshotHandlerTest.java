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

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.DirStructure;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.Target;

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
}