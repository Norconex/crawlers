/* Copyright 2017-2022 Norconex Inc.
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

import static java.io.InputStream.nullInputStream;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class UUIDTaggerTest {

    @Test
    void testWriteRead() {
        var tagger = new UUIDTagger();
        tagger.setToField("field1");
        tagger.setOnSet(PropertySetter.REPLACE);
        XML.assertWriteRead(tagger, "handler");
    }

    @Test
    void testUUIDTagger() throws ImporterHandlerException {
        var t = new UUIDTagger();
        t.setToField("result");
        var props = new Properties();
        t.tagDocument(TestUtil.newHandlerDoc("ref", nullInputStream(), props),
                nullInputStream(), ParseState.POST);
        assertThat(props.getStrings("result")).isNotEmpty();
    }
}
