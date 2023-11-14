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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.importer.TestUtil;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class DateFormatTransformerTest {

    @Test
    void testMultiFromFormatt() throws IOException {
        var dateISOFormat = "yyyy-MM-dd'T'HH:mm:ss";
        var dateEPOCHFormat = "EPOCH";
        var dateHTTPFormat = "EEE, dd MMM yyyy HH:mm:ss";


        var dateISO = "2001-10-10T11:32:21";
        var dateEPOCH = "1002713541000";
        var dateHTTP = "Wed, 10 Oct 2001 11:32:21";

        var meta = new Properties();
        meta.add("dateISO", dateISO);
        meta.add("dateEPOCH", dateEPOCH);
        meta.add("dateHTTP",  dateHTTP);

        var t = new DateFormatTransformer();
        var cfg = t.getConfiguration();
        cfg.setToField("date");
        cfg.setOnSet(PropertySetter.REPLACE);
        cfg.setKeepBadDates(false);

        // Test ISO to EPOCH
        cfg.setFromField("dateISO");
        cfg.setToFormat(dateEPOCHFormat);
        cfg.setFromFormats(
                List.of(dateHTTPFormat, dateEPOCHFormat, dateISOFormat));

        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(dateEPOCH, meta.getString("date"));

        // Test EPOCH to ISO
        meta.remove("date");
        cfg.setFromField("dateEPOCH");
        cfg.setToFormat(dateISOFormat);
        cfg.setFromFormats(
                List.of(dateHTTPFormat, dateISOFormat, dateEPOCHFormat));
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(dateISO, meta.getString("date"));

        // Test HTTP to ISO
        meta.remove("date");
        cfg.setFromField("dateHTTP");
        cfg.setToFormat(dateISOFormat);
        cfg.setFromFormats(
                List.of(dateISOFormat, dateEPOCHFormat, dateHTTPFormat));
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(dateISO, meta.getString("date"));

        // Test No match
        meta.remove("date");
        cfg.setFromField("dateHTTP");
        cfg.setToFormat(dateISOFormat);
        cfg.setFromFormats(List.of(dateISOFormat, dateEPOCHFormat));
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(null, meta.getString("date"));

    }

    @Test
    void testDateFormat() throws IOException {
        var meta = new Properties();
        meta.add("datefield1", "2001-10-10T11:32:21");
        meta.add("datefield2", "1002713541000");

        DateFormatTransformer t;

        t = new DateFormatTransformer();
        var cfg = t.getConfiguration();
        cfg.setOnSet(PropertySetter.REPLACE);
        cfg.setFromField("datefield1");
        cfg.setToField("tofield1");
        cfg.setFromFormats(List.of("yyyy-MM-dd'T'HH:mm:ss"));
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("1002713541000", meta.getString("tofield1"));

        t = new DateFormatTransformer();
        cfg = t.getConfiguration();
        cfg.setOnSet(PropertySetter.REPLACE);
        cfg.setFromField("datefield1");
        cfg.setToField("tofield2");
        cfg.setFromFormats(List.of("yyyy-MM-dd'T'HH:mm:ss"));
        cfg.setToFormat("yyyy/MM/dd");
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("2001/10/10", meta.getString("tofield2"));

        t = new DateFormatTransformer();
        cfg = t.getConfiguration();
        cfg.setOnSet(PropertySetter.REPLACE);
        cfg.setFromField("datefield2");
        cfg.setToField("tofield3");
        cfg.setFromFormats(Arrays.asList((String) null));
        cfg.setToFormat("yyyy/MM/dd");
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("2001/10/10", meta.getString("tofield3"));
    }

    @Test
    void testLocalizedDateFormatting() throws IOException {
        Properties meta;
        DateFormatTransformer t;

        meta = new Properties();
        meta.add("sourceField", "2001-04-10T11:32:21");
        t = new DateFormatTransformer();
        var cfg = t.getConfiguration();
        cfg.setOnSet(PropertySetter.REPLACE);
        cfg.setFromField("sourceField");
        cfg.setToField("targetField");
        cfg.setFromFormats(List.of("yyyy-MM-dd'T'HH:mm:ss"));
        cfg.setToFormat("EEE, dd MMM yyyy");
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(
                "Tue, 10 Apr 2001", meta.getString("targetField"));

        meta = new Properties();
        meta.add("sourceField", "2001-04-10T11:32:21");
        t = new DateFormatTransformer();
        cfg = t.getConfiguration();
        cfg.setOnSet(PropertySetter.REPLACE);
        cfg.setFromField("sourceField");
        cfg.setToField("targetField");
        cfg.setFromFormats(List.of("yyyy-MM-dd'T'HH:mm:ss"));
        cfg.setToFormat("EEE, dd MMM yyyy");
        cfg.setToLocale(Locale.CANADA_FRENCH);
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(
                "mar., 10 avr. 2001", meta.getString("targetField"));
    }

    @Test
    void testLocalizedDateParsing() throws IOException {
        Properties meta;
        DateFormatTransformer t;

        meta = new Properties();
        meta.add("sourceField", "Tue, 10 Apr 2001");
        t = new DateFormatTransformer();
        var cfg = t.getConfiguration();
        cfg.setOnSet(PropertySetter.REPLACE);
        cfg.setFromField("sourceField");
        cfg.setToField("targetField");
        cfg.setFromFormats(List.of("EEE, dd MMM yyyy"));
        cfg.setToFormat("yyyy-MM-dd");
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("2001-04-10", meta.getString("targetField"));

        meta = new Properties();
        meta.add("sourceField", "mar., 10 avr. 2001");
        t = new DateFormatTransformer();
        cfg = t.getConfiguration();
        cfg.setOnSet(PropertySetter.REPLACE);
        cfg.setFromField("sourceField");
        cfg.setFromLocale(Locale.CANADA_FRENCH);
        cfg.setToField("targetField");
        cfg.setFromFormats(List.of("EEE, dd MMM yyyy"));
        cfg.setToFormat("yyyy-MM-dd");
        TestUtil.transform(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("2001-04-10", meta.getString("targetField"));
    }
    @Test
    void testWriteRead() {
        var t = new DateFormatTransformer();
        var cfg = t.getConfiguration();
        cfg.setFromField("fromField1");
        cfg.setToField("toField1");
        cfg.setFromFormats(List.of("yyyy-MM-dd", "anotherOne", "aThirdOne"));
        cfg.setToFormat("yyyy-MM");
        cfg.setKeepBadDates(true);
        cfg.setOnSet(PropertySetter.REPLACE);
        BeanMapper.DEFAULT.assertWriteRead(t);
    }
}
