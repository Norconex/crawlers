/* Copyright 2014-2023 Norconex Inc.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.parser.ParseState;

class LanguageTransformerTest {

    private static Map<String, String> sampleTexts;

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        sampleTexts = new HashMap<>();
        sampleTexts.put("en", "just a bit of text");
        sampleTexts.put("fr", "juste un peu de texte");
        sampleTexts.put("it", "solo un po 'di testo");
        sampleTexts.put("es", "sólo un poco de texto");
    }

    @AfterAll
    static void tearDownAfterClass() throws Exception {
        sampleTexts.clear();
        sampleTexts = null;
    }

    @Test
    void testNonMatchingDocLanguage() throws IOException {
        var factory = new CachedStreamFactory(10 * 1024, 10 * 1024);
        var t = new LanguageTransformer();
        t.getConfiguration().setLanguages(Arrays.asList("fr", "it"));
        var meta = new Properties();

        t.accept(TestUtil.newDocContext(
                "n/a",
                factory.newInputStream(sampleTexts.get("en")),
                meta,
                ParseState.POST));
        Assertions.assertNotEquals("en", meta.getString(DocMetadata.LANGUAGE));
    }

    @Test
    void testDefaultLanguageDetection() throws IOException {
        var factory = new CachedStreamFactory(10 * 1024, 10 * 1024);
        var t = new LanguageTransformer();
        t.getConfiguration().setLanguages(
                Arrays.asList("en", "fr", "it", "es"));
        var meta = new Properties();

        for (String lang : sampleTexts.keySet()) {
            t.accept(TestUtil.newDocContext(
                    "n/a",
                    factory.newInputStream(sampleTexts.get(lang)),
                    meta,
                    ParseState.POST));
            Assertions.assertEquals(lang, meta.getString(DocMetadata.LANGUAGE));
        }
    }

    @Test
    void testWriteRead() {
        var t = new LanguageTransformer();
        t.getConfiguration()
            .setKeepProbabilities(true)
            .setFallbackLanguage("fr");

        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(t));

        t.getConfiguration().setLanguages(Arrays.asList("it", "br", "en"));
        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(t));
    }

    @Test
    void testSortOrder() throws IOException {
        var factory = new CachedStreamFactory(10 * 1024, 10 * 1024);
        var t = new LanguageTransformer();
        t.getConfiguration()
            .setKeepProbabilities(true)
            .setLanguages(Arrays.asList("en", "fr", "nl"));
        var meta = new Properties();
        var content = """
            Alice fing an sich zu langweilen; sie saß schon lange bei ihrer\s\
            Schwester am Ufer und hatte nichts zu thun. Das Buch, das ihre\s\
            Schwester las, gefiel ihr nicht; denn es waren weder Bilder noch\s\
            [2] Gespräche darin. „Und was nützen Bücher,“ dachte Alice, „ohne\s\
            Bilder und Gespräche?“

            Sie überlegte sich eben, (so gut es ging, denn sie war schläfrig\s\
            und dumm von der Hitze,) ob es der Mühe werth sei aufzustehen und\s\
            Gänseblümchen zu pflücken, um eine Kette damit zu machen, als\s\
            plötzlich ein weißes Kaninchen mit rothen Augen dicht an ihr\s\
            vorbeirannte.

            This last line is purposely in English.""";
        t.accept(TestUtil.newDocContext(
                "n/a",
                factory.newInputStream(content),
                meta,
                ParseState.POST));
        Assertions.assertEquals("nl", meta.getString(DocMetadata.LANGUAGE));
    }
}
