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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class LanguageTaggerTest {

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
    void testNonMatchingDocLanguage() throws ImporterHandlerException {
        var factory =
                new CachedStreamFactory(10 * 1024, 10 * 1024);
        var tagger = new LanguageTagger();
        tagger.setLanguages(Arrays.asList("fr", "it"));
        var doc = new Doc(
                "n/a", factory.newInputStream(sampleTexts.get("en")));

        tagger.tagDocument(
                new HandlerDoc(doc), doc.getInputStream(), ParseState.POST);
        Assertions.assertNotEquals(
                "en", doc.getMetadata().get(DocMetadata.LANGUAGE));
    }

    @Test
    void testDefaultLanguageDetection() throws ImporterHandlerException {
        var factory =
                new CachedStreamFactory(10 * 1024, 10 * 1024);
        var tagger = new LanguageTagger();
        tagger.setLanguages(Arrays.asList("en", "fr", "it", "es"));

        for (String lang : sampleTexts.keySet()) {
            var doc = new Doc(
                    "n/a", factory.newInputStream(sampleTexts.get(lang)));
            tagger.tagDocument(
                    new HandlerDoc(doc), doc.getInputStream(), ParseState.POST);
            Assertions.assertEquals(
                    lang, doc.getMetadata().getString(DocMetadata.LANGUAGE));
        }
    }

    @Test
    void testWriteRead() {
        var tagger = new LanguageTagger();
        tagger.setKeepProbabilities(true);
        tagger.setFallbackLanguage("fr");

        XML.assertWriteRead(tagger, "handler");

        tagger.setLanguages(Arrays.asList("it", "br", "en"));
        XML.assertWriteRead(tagger, "handler");
    }

    @Test
    void testSortOrder() throws ImporterHandlerException {
        var factory =
                new CachedStreamFactory(10 * 1024, 10 * 1024);
        var tagger = new LanguageTagger();
        tagger.setKeepProbabilities(true);
        tagger.setLanguages(Arrays.asList("en", "fr", "nl"));
        var doc = new Doc(
                "n/a", factory.newInputStream(
            "Alice fing an sich zu langweilen; sie saß schon lange bei ihrer "
          + "Schwester am Ufer und hatte nichts zu thun. Das Buch, das ihre "
          + "Schwester las, gefiel ihr nicht; denn es waren weder Bilder noch "
          + "[2] Gespräche darin. „Und was nützen Bücher,“ dachte Alice, „ohne "
          + "Bilder und Gespräche?“\n\n"
          + "Sie überlegte sich eben, (so gut es ging, denn sie war schläfrig "
          + "und dumm von der Hitze,) ob es der Mühe werth sei aufzustehen und "
          + "Gänseblümchen zu pflücken, um eine Kette damit zu machen, als "
          + "plötzlich ein weißes Kaninchen mit rothen Augen dicht an ihr "
          + "vorbeirannte.\n\n"
          + "This last line is purposely in English."));
        tagger.tagDocument(
                new HandlerDoc(doc), doc.getInputStream(), ParseState.POST);
        Assertions.assertEquals("nl",
                doc.getMetadata().getString(DocMetadata.LANGUAGE));
    }
}
