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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class DocumentLengthTaggerTest {

    @Test
    void testDocumentLengthTagger() throws ImporterHandlerException {
        var tagger = new DocumentLengthTagger();
        tagger.setToField("theLength");
        tagger.setOnSet(PropertySetter.REPLACE);

        assertThatNoException().isThrownBy(() -> {
            XML.assertWriteRead(tagger, "handler");
        });


        var props = new Properties();
        assertThatNoException().isThrownBy(() -> {
            tagger.tagDocument(TestUtil.newHandlerDoc(props),
                    TestUtil.toCachedInputStream("four"),
                    ParseState.PRE);
        });
        assertThat(props.getLong("theLength")).isEqualTo(4);

        assertThatNoException().isThrownBy(() -> {
            tagger.tagDocument(TestUtil.newHandlerDoc(props),
                    TestUtil.toInputStream("fives"),
                    ParseState.PRE);
        });
        assertThat(props.getLong("theLength")).isEqualTo(5);
    }
}
