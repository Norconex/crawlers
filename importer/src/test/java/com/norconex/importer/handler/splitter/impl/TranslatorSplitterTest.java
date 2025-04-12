/* Copyright 2022-2024 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.language.translate.Translator;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.DocHandlerException;
import com.norconex.importer.handler.splitter.impl.TranslatorSplitter.TranslatorStrategy;

class TranslatorSplitterTest {

    private static final List<String> APIS = List.of(
            TranslatorSplitterConfig.API_MICROSOFT,
            TranslatorSplitterConfig.API_GOOGLE,
            TranslatorSplitterConfig.API_LINGO24,
            TranslatorSplitterConfig.API_MOSES,
            TranslatorSplitterConfig.API_YANDEX);

    @Test
    void testTranslatorMap() throws Exception {
        APIS.stream().forEach(api -> {
            var splitter = new TranslatorSplitter();
            splitter.getConfiguration().setApi(api);

            var strategy = splitter.getTranslatorStrategy();

            assertThat(strategy.createTranslator()).isNotNull();

            assertThatExceptionOfType(DocHandlerException.class)
                    .isThrownBy(strategy::validateProperties);

            // add missing config
            splitter.getConfiguration()
                    .setApiKey("mockapikey")
                    .setUserKey("mockUserKey")
                    .setClientId("mockClientId")
                    .setClientSecret("mockClientSecret")
                    .setSourceLanguage("en")
                    .setTargetLanguages(List.of("fr"))
                    .setSmtPath("mockSmtPath")
                    .setScriptPath("mockScriptPath");

            assertThatNoException()
                    .isThrownBy(strategy::validateProperties);
        });
    }

    @Test
    void testTranslateContent() throws Exception {

        var splitter = new TranslatorSplitter(createMockTranslators());

        splitter.getConfiguration()
                .setApiKey("mockapikey")
                .setApi(TranslatorSplitterConfig.API_MICROSOFT)
                .setClientId("mockClientId")
                .setClientSecret("mockClientSecret")
                .setSourceLanguage("en")
                .setTargetLanguages(List.of("fr"));

        var hdlCtx = TestUtil.newHandlerContext("n/a", "Some content");
        splitter.split(hdlCtx);

        assertThat(hdlCtx.childDocs()).hasSize(1);

        var translation =
                TestUtil.getContentAsString(hdlCtx.childDocs().get(0));

        assertThat(translation).contains("translated");
    }

    @Test
    void testTranslateField() throws Exception {

        var splitter = new TranslatorSplitter(createMockTranslators());

        splitter.getConfiguration()
                .setApiKey("mockapikey")
                .setApi(TranslatorSplitterConfig.API_MICROSOFT)
                .setClientId("mockClientId")
                .setClientSecret("mockClientSecret")
                .setSourceLanguage("en")
                .setTargetLanguages(List.of("fr"))
                .setFieldMatcher(TextMatcher.regex("field.*"));

        var hdlCtx = TestUtil.newHandlerContext("n/a", "Some content");
        hdlCtx.metadata().set("fieldA", "Some field content A");
        hdlCtx.metadata().set("fieldB", "Some field content B");
        hdlCtx.metadata().set("fieldC", "Some field content C");
        splitter.split(hdlCtx);

        assertThat(hdlCtx.childDocs()).isEmpty();
        assertThat(hdlCtx.metadata()).hasSize(6);
        assertThat(hdlCtx.metadata().get("fieldA.fr").get(0))
                .isEqualTo("translated");
        assertThat(hdlCtx.metadata().get("fieldB.fr").get(0))
                .isEqualTo("translated");
        assertThat(hdlCtx.metadata().get("fieldC.fr").get(0))
                .isEqualTo("translated");
    }

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

    private Map<String, TranslatorStrategy> createMockTranslators() {

        var mockStrategy = new TranslatorStrategy() {
            @Override
            public Translator createTranslator() {
                return new Translator() {

                    @Override
                    public String translate(
                            String text,
                            String sourceLanguage,
                            String targetLanguage)
                            throws TikaException, IOException {
                        if (text.contains("[")) {
                            return text.replaceAll("\\[.*?\\]", "[translated]");
                        }
                        return "translated";
                    }

                    @Override
                    public String translate(String text, String targetLanguage)
                            throws TikaException, IOException {
                        return translate(text, null, targetLanguage);
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }
                };
            }

            @Override
            public void validateProperties() throws DocHandlerException {
                //NOOP
            }
        };

        return Map.of(
                "microsoft", mockStrategy,
                "google", mockStrategy,
                "lingo24", mockStrategy,
                "moses", mockStrategy,
                "yandex", mockStrategy);
    }

}
