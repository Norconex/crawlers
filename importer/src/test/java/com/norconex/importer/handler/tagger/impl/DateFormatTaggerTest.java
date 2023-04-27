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

import java.util.Locale;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class DateFormatTaggerTest {

    @Test
    void testMultiFromFormatTagger() throws ImporterHandlerException {
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

        var t = new DateFormatTagger();
        t.setToField("date");
        t.setOnSet(PropertySetter.REPLACE);
        t.setKeepBadDates(false);

        // Test ISO to EPOCH
        t.setFromField("dateISO");
        t.setToFormat(dateEPOCHFormat);
        t.setFromFormats(dateHTTPFormat, dateEPOCHFormat, dateISOFormat);

        TestUtil.tag(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(dateEPOCH, meta.getString("date"));

        // Test EPOCH to ISO
        meta.remove("date");
        t.setFromField("dateEPOCH");
        t.setToFormat(dateISOFormat);
        t.setFromFormats(dateHTTPFormat, dateISOFormat, dateEPOCHFormat);
        TestUtil.tag(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(dateISO, meta.getString("date"));

        // Test HTTP to ISO
        meta.remove("date");
        t.setFromField("dateHTTP");
        t.setToFormat(dateISOFormat);
        t.setFromFormats(dateISOFormat, dateEPOCHFormat, dateHTTPFormat);
        TestUtil.tag(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(dateISO, meta.getString("date"));

        // Test No match
        meta.remove("date");
        t.setFromField("dateHTTP");
        t.setToFormat(dateISOFormat);
        t.setFromFormats(dateISOFormat, dateEPOCHFormat);
        TestUtil.tag(t, "n/a", meta, ParseState.POST);
        Assertions.assertEquals(null, meta.getString("date"));

    }

    @Test
    void testDateFormat() throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("datefield1", "2001-10-10T11:32:21");
        meta.add("datefield2", "1002713541000");

        DateFormatTagger tagger;

        tagger = new DateFormatTagger();
        tagger.setOnSet(PropertySetter.REPLACE);
        tagger.setFromField("datefield1");
        tagger.setToField("tofield1");
        tagger.setFromFormats("yyyy-MM-dd'T'HH:mm:ss");
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("1002713541000", meta.getString("tofield1"));

        tagger = new DateFormatTagger();
        tagger.setOnSet(PropertySetter.REPLACE);
        tagger.setFromField("datefield1");
        tagger.setToField("tofield2");
        tagger.setFromFormats("yyyy-MM-dd'T'HH:mm:ss");
        tagger.setToFormat("yyyy/MM/dd");
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("2001/10/10", meta.getString("tofield2"));

        tagger = new DateFormatTagger();
        tagger.setOnSet(PropertySetter.REPLACE);
        tagger.setFromField("datefield2");
        tagger.setToField("tofield3");
        tagger.setFromFormats((String) null);
        tagger.setToFormat("yyyy/MM/dd");
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("2001/10/10", meta.getString("tofield3"));
    }

    @Test
    void testLocalizedDateFormatting() throws ImporterHandlerException {
        Properties meta;
        DateFormatTagger tagger;

        meta = new Properties();
        meta.add("sourceField", "2001-04-10T11:32:21");
        tagger = new DateFormatTagger();
        tagger.setOnSet(PropertySetter.REPLACE);
        tagger.setFromField("sourceField");
        tagger.setToField("targetField");
        tagger.setFromFormats("yyyy-MM-dd'T'HH:mm:ss");
        tagger.setToFormat("EEE, dd MMM yyyy");
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("Tue, 10 Apr 2001", meta.getString("targetField"));

        meta = new Properties();
        meta.add("sourceField", "2001-04-10T11:32:21");
        tagger = new DateFormatTagger();
        tagger.setOnSet(PropertySetter.REPLACE);
        tagger.setFromField("sourceField");
        tagger.setToField("targetField");
        tagger.setFromFormats("yyyy-MM-dd'T'HH:mm:ss");
        tagger.setToFormat("EEE, dd MMM yyyy");
        tagger.setToLocale(Locale.CANADA_FRENCH);
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("mar., 10 avr. 2001", meta.getString("targetField"));
    }

    @Test
    void testLocalizedDateParsing() throws ImporterHandlerException {
        Properties meta;
        DateFormatTagger tagger;

        meta = new Properties();
        meta.add("sourceField", "Tue, 10 Apr 2001");
        tagger = new DateFormatTagger();
        tagger.setOnSet(PropertySetter.REPLACE);
        tagger.setFromField("sourceField");
        tagger.setToField("targetField");
        tagger.setFromFormats("EEE, dd MMM yyyy");
        tagger.setToFormat("yyyy-MM-dd");
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("2001-04-10", meta.getString("targetField"));

        meta = new Properties();
        meta.add("sourceField", "mar., 10 avr. 2001");
        tagger = new DateFormatTagger();
        tagger.setOnSet(PropertySetter.REPLACE);
        tagger.setFromField("sourceField");
        tagger.setFromLocale(Locale.CANADA_FRENCH);
        tagger.setToField("targetField");
        tagger.setFromFormats("EEE, dd MMM yyyy");
        tagger.setToFormat("yyyy-MM-dd");
        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);
        Assertions.assertEquals("2001-04-10", meta.getString("targetField"));
    }
    @Test
    void testWriteRead() {
        var tagger = new DateFormatTagger();
        tagger.setFromField("fromField1");
        tagger.setToField("toField1");
        tagger.setFromFormats("yyyy-MM-dd", "anotherOne", "aThirdOne");
        tagger.setToFormat("yyyy-MM");
        tagger.setKeepBadDates(true);
        tagger.setOnSet(PropertySetter.REPLACE);
        XML.assertWriteRead(tagger, "handler");
    }
}
