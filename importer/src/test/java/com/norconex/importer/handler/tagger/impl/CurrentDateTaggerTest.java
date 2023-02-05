/* Copyright 2015-2022 Norconex Inc.
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

import java.util.Locale;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class CurrentDateTaggerTest {


    @Test
    void testCurrentDateTagger() throws ImporterHandlerException {
        var now = System.currentTimeMillis();
        Sleeper.sleepMillis(10);// to make sure time has passed

        Properties meta;
        CurrentDateTagger tagger;

        meta = new Properties();
        tagger = new CurrentDateTagger();
        tagger.setFormat("yyyy-MM-dd'T'HH:mm:ss");
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertTrue(
                meta.getString(DocMetadata.IMPORTED_DATE).matches(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
                "Returned date format does not match");

        meta = new Properties();
        tagger = new CurrentDateTagger();
        tagger.setFormat("EEEE");
        tagger.setLocale(Locale.CANADA_FRENCH);
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertTrue(
                ArrayUtils.contains(new String[]{
                        "lundi", "mardi", "mercredi", "jeudi", "vendredi",
                        "samedi", "dimanche"},
                        meta.getString(DocMetadata.IMPORTED_DATE)),
                "Returned date format does not match");

        meta = new Properties();
        meta.add("existingField", "1002727941000");
        tagger = new CurrentDateTagger();
        tagger.setOnSet(PropertySetter.REPLACE);
        tagger.setToField("existingField");
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(
                1, meta.getLongs("existingField").size(),
                "Invalid overwritten number of date values");
        Assertions.assertTrue(
                meta.getLong("existingField") > now,
                "Invalid overwritten date created");

        meta = new Properties();
        meta.add("existingField", "1002727941000");
        tagger = new CurrentDateTagger();
        tagger.setOnSet(PropertySetter.APPEND);
        tagger.setToField("existingField");
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(
                2, meta.getLongs("existingField").size(),
                "Invalid added number of date values");
        var longs = meta.getLongs("existingField");
        for (Long dateLong : longs) {
            if (dateLong == 1002727941000L) {
                continue;
            }
            Assertions.assertTrue(
                    dateLong > now, "Invalid added date created");
        }
    }

    @Test
    void testWriteRead() {
        var tagger = new CurrentDateTagger();
        tagger.setToField("field1");
        tagger.setFormat("yyyy-MM-dd");
        tagger.setOnSet(PropertySetter.REPLACE);
        XML.assertWriteRead(tagger, "handler");
    }
}
