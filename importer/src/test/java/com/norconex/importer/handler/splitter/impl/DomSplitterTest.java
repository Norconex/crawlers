/* Copyright 2015-2023 Norconex Inc.
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
package com.norconex.importer.handler.splitter.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class DomSplitterTest {

    @Test
    void testHtmlDOMSplit()
            throws ImporterHandlerException, IOException {
        var html = ResourceLoader.getHtmlString(getClass());
        var splitter = new DomSplitter();
        splitter.getConfiguration().setSelector("div.person");
        var docs = split(html, splitter);

        Assertions.assertEquals(3, docs.size());
        var content = TestUtil.getContentAsString(docs.get(2));
        Assertions.assertTrue(content.contains("Dalton"));
    }


    @Test
    void testXmlDOMSplit()
            throws ImporterHandlerException, IOException {

        var xml = ResourceLoader.getXmlString(getClass());

        var splitter = new DomSplitter();
        splitter.getConfiguration().setSelector("person");
        var docs = split(xml, splitter);

        Assertions.assertEquals(3, docs.size());

        var content = TestUtil.getContentAsString(docs.get(2));
        Assertions.assertTrue(content.contains("Dalton"));
    }

    private List<Doc> split(String text, DomSplitter splitter)
            throws ImporterHandlerException {
        var metadata = new Properties();
        var is = IOUtils.toInputStream(text, StandardCharsets.UTF_8);
        return splitter.splitDocument(
                TestUtil.newHandlerDoc("n/a", is, metadata),
                is, NullOutputStream.INSTANCE, ParseState.PRE);
    }

    @Test
        void testWriteRead() {
        var splitter = new DomSplitter();
        splitter.getConfiguration().setSelector("blah");
        splitter.getConfiguration().setContentTypeMatcher(
                TextMatcher.basic("value").partial().ignoreCase());
        BeanMapper.DEFAULT.assertWriteRead(splitter);
    }
}
