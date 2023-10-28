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
package com.norconex.importer.handler.splitter.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.Doc;

class XmlStreamSplitterTest {

    private final String sampleXML = """
    	 <animals>
    	   <species name="mouse">
    	     <animal>
    	       <name>Itchy</name>
    	       <race>cartoon</race>
    	     </animal>
    	   </species>
    	   <species name="cat">
    	     <animal>
    	       <name>Scratchy</name>
    	       <race>cartoon</race>
    	     </animal>
    	   </species>
    	 </animals>""";

    @Test
    void testStreamSplit() throws IOException, IOException {

        var splitter = new XmlStreamSplitter();
        splitter.getConfiguration()
            .setPath("/animals/species/animal")
            .setContentTypeMatcher(TextMatcher.regex(".*"));
        var docs = split(sampleXML, splitter);

        Assertions.assertEquals(2, docs.size());
        var content = TestUtil.getContentAsString(docs.get(1));
        Assertions.assertTrue(content.contains("Scratchy"));
    }

    @Test
    void testErrorsAndEmpty() throws IOException, IOException {
        var splitter = new XmlStreamSplitter();
        splitter.getConfiguration()
            .setPath("/a/b/c")
            .setContentTypeMatcher(TextMatcher.regex(".*"));

        // test failing stream
        assertThatExceptionOfType(IOException.class).isThrownBy(
                () -> splitter.accept(TestUtil.newDocContext()));

        // Test non applicable
        splitter.getConfiguration().setContentTypeMatcher(
                TextMatcher.basic("IdontExists"));
        var docCtx = TestUtil.newDocContext(
                "N/A",
                TestUtil.toCachedInputStream(sampleXML));
        splitter.accept(docCtx);
        assertThat(docCtx.childDocs()).isEmpty();
    }

    private List<Doc> split(String text, XmlStreamSplitter splitter)
            throws IOException {
        var metadata = new Properties();
        var is = IOUtils.toInputStream(text, StandardCharsets.UTF_8);

        var docCtx = TestUtil.newDocContext("N/A", is, metadata);
        splitter.accept(docCtx);
        return docCtx.childDocs();
    }

    @Test
    void testWriteRead() {
        var splitter = new XmlStreamSplitter();
        splitter.getConfiguration()
            .setPath("blah")
            .setContentTypeMatcher(
                    TextMatcher.basic("value").partial().ignoreCase());
        BeanMapper.DEFAULT.assertWriteRead(splitter);
    }
}
