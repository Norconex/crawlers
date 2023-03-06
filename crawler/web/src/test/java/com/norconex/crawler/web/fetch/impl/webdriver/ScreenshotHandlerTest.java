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

import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.web.fetch.util.DocImageHandler.DirStructure;
import com.norconex.crawler.web.fetch.util.DocImageHandler.Target;

class ScreenshotHandlerTest {

    @Test
    void testWriteRead() {
        var h = new ScreenshotHandler();
        h.setCssSelector("body .content");
        h.setImageFormat("jpg");
        h.setTargetDir(Paths.get("/tmp/blah"));
        h.setTargetDirStructure(DirStructure.URL2PATH);
        h.setTargetDirField("docImage");
        h.setTargetMetaField("docMeta");
        h.setTargets(List.of(Target.DIRECTORY, Target.METADATA));

        XML.assertWriteRead(h, "screenshot");
    }
}