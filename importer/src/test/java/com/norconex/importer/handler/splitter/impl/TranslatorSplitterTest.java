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
package com.norconex.importer.handler.splitter.impl;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.xml.XML;

class TranslatorSplitterTest {

    @Test
    void testWriteRead() {
        var ts = new TranslatorSplitter();
        ts.setApi("google");
        ts.setApiKey("myapikey");
        ts.setClientId("myclientid");
        ts.setClientSecret("myclientsecret");
        ts.setFieldsToTranslate(List.of("field1", "field2"));
        ts.setIgnoreNonTranslatedFields(true);
        ts.setScriptPath("myscriptpath");
        ts.setSmtPath("mysmtpath");
        ts.setSourceLanguage("fr");
        ts.setSourceLanguageField("mysourcelangfield");
        ts.setTargetLanguages(List.of("en", "it"));
        ts.setUserKey("myuserkey");

        assertThatNoException().isThrownBy(() ->
            XML.assertWriteRead(ts, "handler")
        );
    }
}
