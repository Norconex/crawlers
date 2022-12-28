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
package com.norconex.importer.handler.filter.impl;

import static com.norconex.importer.parser.ParseState.PRE;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.Operator;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.handler.filter.impl.DateMetadataFilter.DynamicFixedDateTimeSupplier;
import com.norconex.importer.handler.filter.impl.DateMetadataFilter.DynamicFloatingDateTimeSupplier;
import com.norconex.importer.handler.filter.impl.DateMetadataFilter.TimeUnit;

class DateMetadataFilterTest {

    @Test
    void testAcceptDocument()
            throws ImporterHandlerException, ParseException {

        var meta = new Properties();

        DateMetadataFilter filter;

        meta.set("field1", "1980-12-21T12:22:01.123");
        filter = new DateMetadataFilter();
        filter.setFieldMatcher(TextMatcher.basic("field1"));
        filter.setFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

        filter.addCondition(Operator.LOWER_EQUAL, LocalDate.of(
                1980, 12, 21).atStartOfDay(ZoneId.systemDefault()));
        Assertions.assertFalse(TestUtil.filter(filter, "n/a", null, meta, PRE));


        meta.set("field1", "1980-12-21");
        filter = new DateMetadataFilter();
        filter.setFieldMatcher(TextMatcher.basic("field1"));
        filter.setFormat("yyyy-MM-dd");
        filter.addCondition(Operator.LOWER_EQUAL, LocalDate.of(
                1980, 12, 21).atStartOfDay(ZoneId.systemDefault()));
        Assertions.assertTrue(TestUtil.filter(filter, "n/a", null, meta, PRE));


        meta.set("field1", "1980-12-21T12:22:01.123");
        filter = new DateMetadataFilter();
        filter.setFieldMatcher(TextMatcher.basic("field1"));
        filter.setFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        filter.addCondition(Operator.LOWER_EQUAL, LocalDate.of(
                1980, 12, 22).atStartOfDay(ZoneId.systemDefault()));
        Assertions.assertTrue(TestUtil.filter(filter, "n/a", null, meta, PRE));

        meta.set("field1", ZonedDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        filter = new DateMetadataFilter();
        filter.setFieldMatcher(TextMatcher.basic("field1"));
        filter.setFormat("yyyy-MM-dd'T'HH:mm:ss");

        filter.addCondition(
                Operator.GREATER_THAN, new DynamicFixedDateTimeSupplier(
                        TimeUnit.MINUTE, -1, false, null));
        filter.addCondition(
                Operator.LOWER_THAN, new DynamicFixedDateTimeSupplier(
                        TimeUnit.MINUTE, +1, false, null));
        Assertions.assertTrue(TestUtil.filter(filter, "n/a", null, meta, PRE));
    }

    @Test
    void testWriteRead() {
        var filter = new DateMetadataFilter(TextMatcher.basic("field1"));
        filter.setFormat("yyyy-MM-dd");
        filter.setOnMatch(OnMatch.EXCLUDE);
        filter.addCondition(Operator.GREATER_EQUAL, ZonedDateTime.now());
        filter.addCondition(Operator.LOWER_THAN,
                ZonedDateTime.now().plus(10, ChronoUnit.SECONDS));
        // Cannot test equality when condition is fixed since the initialization
        // time will vary. So test with last argument false.
        filter.addCondition(Operator.EQUALS,
                new DynamicFloatingDateTimeSupplier(
                        TimeUnit.YEAR, -2, false, null));
        XML.assertWriteRead(filter, "handler");
    }

    @Test
    void testOperatorDateParsing() {

        var xml = XML.of(
                  "<handler\n"
                + "    class=\"DateMetadataFilter\""
                + "    format=\"yyyy-MM-dd'T'HH:mm:ss'Z'\""
                + "    onMatch=\"exclude\">"
                + "  <fieldMatcher>scan_timestamp</fieldMatcher>"
                + "  <condition operator=\"lt\" date=\"TODAY\"/>"
                + "  <condition operator=\"lt\" date=\"2020-09-27T12:34:56\"/>"
                + "  <condition operator=\"lt\" date=\"2020-09-27\"/>"
                + "</handler>").create();
        var f = new DateMetadataFilter();
        f.loadFromXML(xml);

        var conds = f.getConditions();

        // Assert valid date strings
        Assertions.assertEquals("TODAY",
                conds.get(0).getDateTimeSupplier().toString());
        Assertions.assertEquals("2020-09-27T12:34:56.000",
                conds.get(1).getDateTimeSupplier().toString());
        Assertions.assertEquals("2020-09-27T00:00:00.000",
                conds.get(2).getDateTimeSupplier().toString());

        var today = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
        var dateTime = ZonedDateTime.of(
                2020, 9, 27, 12, 34, 56, 0, today.getZone());
        var date = ZonedDateTime.of(
                2020, 9, 27, 12, 34, 56, 0, today.getZone())
                        .truncatedTo(ChronoUnit.DAYS);

        Assertions.assertEquals(today, conds.get(0).getDateTime());
        Assertions.assertEquals(dateTime, conds.get(1).getDateTime());
        Assertions.assertEquals(date, conds.get(2).getDateTime());
    }

    @Test
    void testAroundNow() throws ImporterHandlerException {
        var now = LocalDateTime.now();
        var then55min = now.minusMinutes(55);
        var then65min = now.minusMinutes(65);

        DateMetadataFilter f;
        var meta = new Properties();
        var xml = XML.of(
                        "<handler\n"
                      + "    class=\"DateMetadataFilter\""
                      + "    format=\"yyyy-MM-dd'T'HH:mm:ss.nnn\""
                      + "    onMatch=\"include\">"
                      + "  <fieldMatcher>docdate</fieldMatcher>"
                      + "  <condition operator=\"gt\" date=\"NOW-1h\"/>"
                      + "</handler>").create();

        f = new DateMetadataFilter();
        f.loadFromXML(xml);

        meta.set("docdate",   then55min.toString());
        Assertions.assertTrue(
                TestUtil.filter(f, "n/a", meta, PRE),
                "55 minutes ago was not younger than an hour ago.");

        meta.set("docdate", then65min.toString());
        Assertions.assertFalse(
                TestUtil.filter(f, "n/a", meta, PRE),
                "65 minutes ago was younger than an hour ago.");

        // Excludes older than 1 hour ago
        xml = XML.of(
                "<handler\n"
              + "    class=\"DateMetadataFilter\""
              + "    format=\"yyyy-MM-dd'T'HH:mm:ss.nnn\""
              + "    onMatch=\"exclude\">"
              + "  <fieldMatcher>docdate</fieldMatcher>"
              + "  <condition operator=\"lt\" date=\"NOW-1h\"/>"
              + "</handler>").create();

        f = new DateMetadataFilter();
        f.loadFromXML(xml);

        meta.set("docdate",   then55min.toString());
        Assertions.assertTrue(
                TestUtil.filter(f, "n/a", meta, PRE),
                "55 minutes ago was older than an hour ago.");

        meta.set("docdate", then65min.toString());
        Assertions.assertFalse(
                TestUtil.filter(f, "n/a", meta, PRE),
                "65 minutes ago was not older than an hour ago.");
    }

    @Test
    void testTimeZones() throws ImporterHandlerException {
        /*
         * About this test (24h):
         *   - Paris is 9 hours ahead of LA (Paris = LA + 9).
         *   - Paris time:  5:00 on December 21th, 2020
         *   - L.A.  time: 20:00 on December 20th, 2020
         */

        DateMetadataFilter f;
        var meta = new Properties();
        var xml = XML.of(
                        "<handler\n"
                      + "    class=\"DateMetadataFilter\""
                      + "    format=\"yyyy-MM-dd'T'HH:mm:ss\""
                      + "    docZoneId=\"America/Los_Angeles\""
                      + "    conditionZoneId=\"Europe/Paris\""
                      + "    onMatch=\"include\">"
                      + "  <fieldMatcher>docdate</fieldMatcher>"
                      + "  <condition operator=\"gt\" "
                      + "date=\"2020-12-21T05:00:00\"/>"
                      + "</handler>").create();

        //--- TEST ---
        // Doc timezone in config: YES
        // Doc timezone in field:  NO

        f = new DateMetadataFilter();
        f.loadFromXML(xml);

        // doc older
        meta.set("docdate", "2020-12-20T19:00:00");
        Assertions.assertFalse(TestUtil.filter(f, "n/a", meta, PRE));
        // doc equals
        meta.set("docdate", "2020-12-20T20:00:00");
        Assertions.assertFalse(TestUtil.filter(f, "n/a", meta, PRE));
        // doc younger
        meta.set("docdate", "2020-12-20T21:00:00");
        Assertions.assertTrue(TestUtil.filter(f, "n/a", meta, PRE));
        // doc much younger
        meta.set("docdate", "2020-12-21T06:00:00");
        Assertions.assertTrue(TestUtil.filter(f, "n/a", meta, PRE));

        //--- TEST ---
        // Doc timezone in config: YES (time zone in field should be ignored).
        // Doc timezone in field:  YES (Tokyo UTC+09:00)

        xml = XML.of(
                "<handler\n"
              + "    class=\"DateMetadataFilter\""
              + "    format=\"yyyy-MM-dd'T'HH:mm:ssZ\""
              + "    docZoneId=\"America/Los_Angeles\""
              + "    conditionZoneId=\"Europe/Paris\""
              + "    onMatch=\"include\">"
              + "  <fieldMatcher>docdate</fieldMatcher>"
              + "  <condition operator=\"gt\" date=\"2020-12-21T05:00:00\"/>"
              + "</handler>").create();
        f = new DateMetadataFilter();
        f.loadFromXML(xml);

        // doc older
        meta.set("docdate", "2020-12-20T19:00:00+0900");
        Assertions.assertFalse(TestUtil.filter(f, "n/a", meta, PRE));
        // doc equals
        meta.set("docdate", "2020-12-20T20:00:00+0900");
        Assertions.assertFalse(TestUtil.filter(f, "n/a", meta, PRE));
        // doc younger
        meta.set("docdate", "2020-12-20T21:00:00+0900");
        Assertions.assertTrue(TestUtil.filter(f, "n/a", meta, PRE));
        // doc much younger
        meta.set("docdate", "2020-12-21T06:00:00+0900");
        Assertions.assertTrue(TestUtil.filter(f, "n/a", meta, PRE));

        //--- TEST ---
        // Doc timezone in config: NO  (time zone in field should be kept).
        // Doc timezone in field:  YES (L.A. UTC-07:00)

        xml = XML.of(
                "<handler\n"
              + "    class=\"DateMetadataFilter\""
              + "    format=\"yyyy-MM-dd'T'HH:mm:sszzz\""
              + "    conditionZoneId=\"Europe/Paris\""
              + "    onMatch=\"include\">"
              + "  <fieldMatcher>docdate</fieldMatcher>"
              + "  <condition operator=\"gt\" date=\"2020-12-21T05:00:00\"/>"
              + "</handler>").create();
        f = new DateMetadataFilter();
        f.loadFromXML(xml);

        // doc older
        meta.set("docdate", "2020-12-20T19:00:00-08:00");
        Assertions.assertFalse(TestUtil.filter(f, "n/a", meta, PRE));
        // doc equals
        meta.set("docdate", "2020-12-20T20:00:00-08:00");
        Assertions.assertFalse(TestUtil.filter(f, "n/a", meta, PRE));
        // doc younger
        meta.set("docdate", "2020-12-20T21:00:00-08:00");
        Assertions.assertTrue(TestUtil.filter(f, "n/a", meta, PRE));
        // doc much younger
        meta.set("docdate", "2020-12-21T06:00:00-08:00");
        Assertions.assertTrue(TestUtil.filter(f, "n/a", meta, PRE));
    }
}
