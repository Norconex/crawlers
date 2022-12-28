/* Copyright 2014-2022 Norconex Inc.
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

import static com.norconex.commons.lang.text.TextMatcher.basic;
import static java.io.InputStream.nullInputStream;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class CopyTaggerTest {

    @Test
    void testWriteRead() {
        var tagger = new CopyTagger();
        tagger.addCopyDetails(new TextMatcher("from1"), "to1",
                PropertySetter.OPTIONAL);
        tagger.addCopyDetails(new TextMatcher("from2"), "to2", null);
        XML.assertWriteRead(tagger, "handler");
    }

    @Test
    void testCopyTagger() throws ImporterHandlerException {
        var tagger = new CopyTagger();
        tagger.addCopyDetails("src1", "trgt1"); // default is append
        tagger.addCopyDetails(basic("src2"), "trgt2", PropertySetter.OPTIONAL);
        tagger.addCopyDetails(basic("src3"), "trgt3", PropertySetter.PREPEND);
        tagger.addCopyDetails(basic("src4"), "trgt4", PropertySetter.REPLACE);

        var props = new Properties();
        props.add("src1", "srcVal1");
        props.add("src2", "srcVal2");
        props.add("src3", "srcVal3");
        props.add("src4", "srcVal4");
        props.add("trgt1", "trgtVal1");
        props.add("trgt2", "trgtVal2");
        props.add("trgt3", "trgtVal3");
        props.add("trgt4", "trgtVal4");

        var doc = TestUtil.newHandlerDoc(props);
        tagger.tagDocument(doc, nullInputStream(), ParseState.POST);

        assertThat(props.getStrings("trgt1"))
            .containsExactly("trgtVal1", "srcVal1");
        assertThat(props.getStrings("trgt2")).containsExactly("trgtVal2");
        assertThat(props.getStrings("trgt3"))
            .containsExactly("srcVal3", "trgtVal3");
        assertThat(props.getStrings("trgt4")).containsExactly("srcVal4");
    }
}
