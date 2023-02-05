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
package com.norconex.importer.handler.condition.impl;

import static com.norconex.importer.parser.ParseState.PRE;
import static org.assertj.core.api.Assertions.assertThat;

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
import com.norconex.importer.handler.condition.impl.DateCondition.DynamicFixedDateTimeSupplier;
import com.norconex.importer.handler.condition.impl.DateCondition.DynamicFloatingDateTimeSupplier;
import com.norconex.importer.handler.condition.impl.DateCondition.TimeUnit;

class DateConditionTest {

    @Test
    void testAcceptDocument()
            throws ImporterHandlerException, ParseException {

        var meta = new Properties();

        DateCondition cond;

        meta.set("field1", "1980-12-21T12:22:01.123");
        cond = new DateCondition();
        cond.setFieldMatcher(TextMatcher.basic("field1"));
        cond.setFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

        cond.setValueMatcher(new DateCondition.ValueMatcher(
                Operator.LOWER_EQUAL,
                () -> LocalDate.of(1980, 12, 21)
                    .atStartOfDay(ZoneId.systemDefault())));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isFalse();

        meta.set("field1", "1980-12-21");
        cond = new DateCondition();
        cond.setFieldMatcher(TextMatcher.basic("field1"));
        cond.setFormat("yyyy-MM-dd");
        cond.setValueMatcher(new DateCondition.ValueMatcher(
                Operator.LOWER_EQUAL,
                () -> LocalDate.of(1980, 12, 21)
                    .atStartOfDay(ZoneId.systemDefault())));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isTrue();

        meta.set("field1", "1980-12-21T12:22:01.123");
        cond = new DateCondition();
        cond.setFieldMatcher(TextMatcher.basic("field1"));
        cond.setFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        cond.setValueMatcher(new DateCondition.ValueMatcher(
                Operator.LOWER_EQUAL,
                () -> LocalDate.of(1980, 12, 22)
                    .atStartOfDay(ZoneId.systemDefault())));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isTrue();

        meta.set("field1", ZonedDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
        cond = new DateCondition();
        cond.setFieldMatcher(TextMatcher.basic("field1"));
        cond.setFormat("yyyy-MM-dd'T'HH:mm:ss");

        // range:
        cond.setValueMatcher(new DateCondition.ValueMatcher(
                Operator.GREATER_THAN,
                new DynamicFixedDateTimeSupplier(
                        TimeUnit.MINUTE, -1, false, null)));
        cond.setValueMatcherRangeEnd(new DateCondition.ValueMatcher(
                Operator.LOWER_THAN,
                new DynamicFixedDateTimeSupplier(
                        TimeUnit.MINUTE, +1, false, null)));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isTrue();
    }

    @Test
    void testWriteRead() {
        var cond = new DateCondition();
        cond.setFieldMatcher(TextMatcher.basic("field1"));
        cond.setFormat("yyyy-MM-dd");
        cond.setValueMatcher(new DateCondition.ValueMatcher(
                Operator.GREATER_EQUAL,
                new DateCondition.StaticDateTimeSupplier(ZonedDateTime.now())));
        // Cannot test equality when condition is fixed since the initialization
        // time will vary. So test with last argument false.
        cond.setValueMatcherRangeEnd(new DateCondition.ValueMatcher(
                Operator.EQUALS,
                new DynamicFloatingDateTimeSupplier(
                        TimeUnit.YEAR, -2, false, null)));
        XML.assertWriteRead(cond, "condition");
    }

    @Test
    void testOperatorDateParsing() {
        var xml = XML.of("""
                <condition
                  class="DateCondition"
                  format="yyyy-MM-dd'T'HH:mm:ss'Z'"
                  docZoneId="America/New_York"
                  conditionZoneId="America/New_York"
                >
                  <fieldMatcher>scan_timestamp</fieldMatcher>
                  <valueMatcher operator="gt" date="2020-09-27T12:34:56"/>
                  <valueMatcher operator="lt" date="TODAY"/>
                </condition>""").create();
        var cond = new DateCondition();
        cond.loadFromXML(xml);

        // Assert valid date strings
        assertThat(cond.getValueMatcher().getDateTimeSupplier())
            .hasToString("2020-09-27T12:34:56.000");
        assertThat(cond.getValueMatcherRangeEnd().getDateTimeSupplier())
            .hasToString("TODAY");

        var today = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
        var dateTime = ZonedDateTime.of(
                2020, 9, 27, 12, 34, 56, 0, today.getZone());

        assertThat(cond.getValueMatcher().getDateTime()).isEqualTo(dateTime);
        assertThat(cond.getValueMatcherRangeEnd().getDateTime())
            .isEqualTo(today);
    }

    @Test
    void testAroundNow() throws ImporterHandlerException {
        var now = LocalDateTime.now();
        var then55min = now.minusMinutes(55);
        var then65min = now.minusMinutes(65);

        DateCondition f;
        var meta = new Properties();
        var xml = XML.of("""
                <condition
                    class="DateCondition"
                    format="yyyy-MM-dd'T'HH:mm:ss.nnn">
                  <fieldMatcher>docdate</fieldMatcher>
                  <valueMatcher operator="gt" date="NOW-1h"/>
                </condition>""").create();

        f = new DateCondition();
        f.loadFromXML(xml);

        meta.set("docdate",   then55min.toString());
        Assertions.assertTrue(
                TestUtil.condition(f, "n/a", meta, PRE),
                "55 minutes ago was not younger than an hour ago.");

        meta.set("docdate", then65min.toString());
        Assertions.assertFalse(
                TestUtil.condition(f, "n/a", meta, PRE),
                "65 minutes ago was younger than an hour ago.");

        // Excludes older than 1 hour ago
        xml = XML.of("""
                <condition
                    class="DateCondition"
                    format="yyyy-MM-dd'T'HH:mm:ss.nnn">
                  <fieldMatcher>docdate</fieldMatcher>
                  <valueMatcher operator="lt" date="NOW-1h"/>
                </condition>""").create();

        f = new DateCondition();
        f.loadFromXML(xml);

        meta.set("docdate",   then55min.toString());
        Assertions.assertFalse(
                TestUtil.condition(f, "n/a", meta, PRE),
                "55 minutes ago was older than an hour ago.");

        meta.set("docdate", then65min.toString());
        Assertions.assertTrue(
                TestUtil.condition(f, "n/a", meta, PRE),
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

        DateCondition f;
        var meta = new Properties();
        var xml = XML.of("""
                <condition
                    class="DateCondition"
                    format="yyyy-MM-dd'T'HH:mm:ss"
                    docZoneId="America/Los_Angeles"
                    conditionZoneId="Europe/Paris">
                  <fieldMatcher>docdate</fieldMatcher>
                  <valueMatcher operator="gt" date="2020-12-21T05:00:00"/>
                </condition>""").create();

        //--- TEST ---
        // Doc timezone in config: YES
        // Doc timezone in field:  NO

        f = new DateCondition();
        f.loadFromXML(xml);

        // doc older
        meta.set("docdate", "2020-12-20T19:00:00");
        Assertions.assertFalse(TestUtil.condition(f, "n/a", meta, PRE));
        // doc equals
        meta.set("docdate", "2020-12-20T20:00:00");
        Assertions.assertFalse(TestUtil.condition(f, "n/a", meta, PRE));
        // doc younger
        meta.set("docdate", "2020-12-20T21:00:00");
        Assertions.assertTrue(TestUtil.condition(f, "n/a", meta, PRE));
        // doc much younger
        meta.set("docdate", "2020-12-21T06:00:00");
        Assertions.assertTrue(TestUtil.condition(f, "n/a", meta, PRE));

        //--- TEST ---
        // Doc timezone in config: YES (time zone in field should be ignored).
        // Doc timezone in field:  YES (Tokyo UTC+09:00)

        xml = XML.of("""
                <condition
                    class="DateCondition"
                    format="yyyy-MM-dd'T'HH:mm:ssZ"
                    docZoneId="America/Los_Angeles"
                    conditionZoneId="Europe/Paris">
                  <fieldMatcher>docdate</fieldMatcher>
                  <valueMatcher operator="gt" date="2020-12-21T05:00:00"/>
                </condition>""").create();
        f = new DateCondition();
        f.loadFromXML(xml);

        // doc older
        meta.set("docdate", "2020-12-20T19:00:00+0900");
        Assertions.assertFalse(TestUtil.condition(f, "n/a", meta, PRE));
        // doc equals
        meta.set("docdate", "2020-12-20T20:00:00+0900");
        Assertions.assertFalse(TestUtil.condition(f, "n/a", meta, PRE));
        // doc younger
        meta.set("docdate", "2020-12-20T21:00:00+0900");
        Assertions.assertTrue(TestUtil.condition(f, "n/a", meta, PRE));
        // doc much younger
        meta.set("docdate", "2020-12-21T06:00:00+0900");
        Assertions.assertTrue(TestUtil.condition(f, "n/a", meta, PRE));

        //--- TEST ---
        // Doc timezone in config: NO  (time zone in field should be kept).
        // Doc timezone in field:  YES (L.A. UTC-07:00)

        xml = XML.of("""
                <condition
                    class="DateCondition"
                    format="yyyy-MM-dd'T'HH:mm:sszzz"
                    conditionZoneId="Europe/Paris">
                  <fieldMatcher>docdate</fieldMatcher>
                  <valueMatcher operator="gt" date="2020-12-21T05:00:00"/>
                </condition>""").create();
        f = new DateCondition();
        f.loadFromXML(xml);

        // doc older
        meta.set("docdate", "2020-12-20T19:00:00-08:00");
        Assertions.assertFalse(TestUtil.condition(f, "n/a", meta, PRE));
        // doc equals
        meta.set("docdate", "2020-12-20T20:00:00-08:00");
        Assertions.assertFalse(TestUtil.condition(f, "n/a", meta, PRE));
        // doc younger
        meta.set("docdate", "2020-12-20T21:00:00-08:00");
        Assertions.assertTrue(TestUtil.condition(f, "n/a", meta, PRE));
        // doc much younger
        meta.set("docdate", "2020-12-21T06:00:00-08:00");
        Assertions.assertTrue(TestUtil.condition(f, "n/a", meta, PRE));
    }
}
