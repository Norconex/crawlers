/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.importer.handler.transformer.impl;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.parser.ParseState;

class TextStatisticsTransformerTest {

    @Test
    void testTagTextDocument() throws IOException {

        var txt =
                """
                        White Rabbit checking watch\


                          In another moment down went Alice after it, never once\s\
                        considering how in the world she was to get out again.\


                          The rabbit-hole went straight on like a tunnel for some way,\s\
                        and then dipped suddenly down, so suddenly that Alice had not a\s\
                        moment to think about stopping herself before she found herself\s\
                        falling down a very deep well.\


                        `Well!' thought Alice to herself, `after such a fall as this, I\s\
                        shall think nothing of tumbling down stairs!  How brave they'll\s\
                        all think me at home!  Why, I wouldn't say anything about it,\s\
                        even if I fell off the top of the house!' (Which was very likely\s\
                        true.)""";

        var t = new TextStatisticsTransformer();
        var is = IOUtils.toInputStream(txt, StandardCharsets.UTF_8);

        var meta = new Properties();
        meta.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.handle(TestUtil.newHandlerContext("n/a", is, meta, ParseState.PRE));

        is.close();

        Assertions.assertEquals(
                616,
                (int) meta.getInteger("document.stat.characterCount"));
        Assertions.assertEquals(
                115,
                (int) meta.getInteger("document.stat.wordCount"));
        Assertions.assertEquals(
                8,
                (int) meta.getInteger("document.stat.sentenceCount"));
        Assertions.assertEquals(
                4,
                (int) meta.getInteger("document.stat.paragraphCount"));
        Assertions.assertEquals(
                "4.2",
                meta.getString("document.stat.averageWordCharacterCount"));
        Assertions.assertEquals(
                "77.0",
                meta.getString("document.stat.averageSentenceCharacterCount"));
        Assertions.assertEquals(
                "14.4",
                meta.getString("document.stat.averageSentenceWordCount"));
        Assertions.assertEquals(
                "154.0",
                meta.getString("document.stat.averageParagraphCharacterCount"));
        Assertions.assertEquals(
                "2.0",
                meta.getString("document.stat.averageParagraphSentenceCount"));
        Assertions.assertEquals(
                "28.8",
                meta.getString("document.stat.averageParagraphWordCount"));
    }

    @Test
    void testWriteRead() {
        var t = new TextStatisticsTransformer();
        t.getConfiguration().setFieldMatcher(new TextMatcher("afield"));
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(t));
    }
}
