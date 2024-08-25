/* Copyright 2022-2023 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.text.TextMatcher;

class TranslatorSplitterTest {

    @Test
    void testWriteRead() {
        var ts = new TranslatorSplitter();
        ts.getConfiguration()
                .setApi("google")
                .setApiKey("myapikey")
                .setClientId("myclientid")
                .setClientSecret("myclientsecret")
                .setFieldMatcher(TextMatcher.csv("field1, field2"))
                .setIgnoreNonTranslatedFields(true)
                .setScriptPath("myscriptpath")
                .setSmtPath("mysmtpath")
                .setSourceLanguage("fr")
                .setSourceLanguageField("mysourcelangfield")
                .setTargetLanguages(List.of("en", "it"))
                .setUserKey("myuserkey");

        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(ts));
    }
}
