/* Copyright 2010-2022 Norconex Inc.
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

import static com.norconex.importer.TestUtil.newHandlerDoc;
import static com.norconex.importer.parser.ParseState.PRE;
import static java.io.InputStream.nullInputStream;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.ImporterHandlerException;

class ForceSingleValueTaggerTest {

    @Test
    void testWriteRead() {
        var tagger = new ForceSingleValueTagger();
        tagger.setAction("keepFirst");
        tagger.getFieldMatcher().setPattern("field1|field2|field3");
        XML.assertWriteRead(tagger, "handler");
    }

    @Test
    void testForceSingleValueTagger() throws ImporterHandlerException {

        var tagger = new ForceSingleValueTagger();
        tagger.setFieldMatcher(TextMatcher.basic("a"));

        var props = new Properties();
        props.add("a", 1, 2, 3);
        tagger.setAction("keepFirst");
        tagger.tagDocument(newHandlerDoc(props), nullInputStream(), PRE);
        assertThat(props.getIntegers("a")).containsExactly(1);

        props = new Properties();
        props.add("a", 1, 2, 3);
        tagger.setAction("keepLast");
        tagger.tagDocument(newHandlerDoc(props), nullInputStream(), PRE);
        assertThat(props.getIntegers("a")).containsExactly(3);

        props = new Properties();
        props.add("a", 1, 2, 3);
        tagger.setAction("mergeWith:-");
        tagger.tagDocument(newHandlerDoc(props), nullInputStream(), PRE);
        assertThat(props.getString("a")).isEqualTo("1-2-3");
    }
}
