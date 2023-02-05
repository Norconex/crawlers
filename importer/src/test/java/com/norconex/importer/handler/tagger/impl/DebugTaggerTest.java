/* Copyright 2022 Norconex Inc.
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
package com.norconex.importer.handler.tagger.impl;

import static com.norconex.importer.parser.ParseState.PRE;
import static java.io.InputStream.nullInputStream;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;

class DebugTaggerTest {

    @Test
    void testDebugTagger() {

        var tagger = new DebugTagger();
        tagger.setLogContent(true);
        tagger.setLogFields(List.of("field1", "field2"));
        tagger.setLogLevel("debug");
        tagger.setPrefix("prefix-");

        assertThatNoException().isThrownBy(() -> {
            XML.assertWriteRead(tagger, "handler");
        });


        var props = new Properties();
        props.set("field1", "value1");
        props.set("field2", "value2");
        assertThatNoException().isThrownBy(() -> {
            tagger.tagApplicableDocument(
                    TestUtil.newHandlerDoc(props), nullInputStream(), PRE);
        });
    }
}
