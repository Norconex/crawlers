/* Copyright 2015-2024 Norconex Inc.
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

import java.util.Locale;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class CurrentDateTransformerTest {

    @Test
    void testCurrentDatet() throws IOException {
        var now = System.currentTimeMillis();
        Sleeper.sleepMillis(10);// to make sure time has passed

        Properties meta;
        CurrentDateTransformer t;

        meta = new Properties();
        t = new CurrentDateTransformer();
        t.getConfiguration().setFormat("yyyy-MM-dd'T'HH:mm:ss");
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertTrue(
                meta.getString(DocMetadata.IMPORTED_DATE).matches(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}"),
                "Returned date format does not match");

        meta = new Properties();
        t = new CurrentDateTransformer();
        t.getConfiguration()
                .setFormat("EEEE")
                .setLocale(Locale.CANADA_FRENCH);
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertTrue(
                ArrayUtils.contains(
                        new String[] {
                                "lundi", "mardi", "mercredi", "jeudi",
                                "vendredi",
                                "samedi", "dimanche" },
                        meta.getString(DocMetadata.IMPORTED_DATE)),
                "Returned date format does not match");

        meta = new Properties();
        meta.add("existingField", "1002727941000");
        t = new CurrentDateTransformer();
        t.getConfiguration()
                .setOnSet(PropertySetter.REPLACE)
                .setToField("existingField");
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(
                1, meta.getLongs("existingField").size(),
                "Invalid overwritten number of date values");
        Assertions.assertTrue(
                meta.getLong("existingField") > now,
                "Invalid overwritten date created");

        meta = new Properties();
        meta.add("existingField", "1002727941000");
        t = new CurrentDateTransformer();
        t.getConfiguration()
                .setOnSet(PropertySetter.APPEND)
                .setToField("existingField");
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
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
        var t = new CurrentDateTransformer();
        t.getConfiguration()
                .setToField("field1")
                .setFormat("yyyy-MM-dd")
                .setOnSet(PropertySetter.REPLACE);
        BeanMapper.DEFAULT.assertWriteRead(t);
    }
}
