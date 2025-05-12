/* Copyright 2023-2024 Norconex Inc.
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

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.time.ZonedDateTimeParser;
import com.norconex.importer.handler.condition.impl.DateCondition.TimeUnit;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

/**
 * <p>
 * Creates suppliers of static or dynamic dates, from a formatted string.
 * </p>
 * <p>
 * To successfully parse a date string, you can specify a date format,
 * as per the formatting options found on {@link DateTimeFormatter}.
 * The default format when not specified is EPOCH (the difference, measured in
 * milliseconds, between the date and midnight, January 1, 1970).</p>
 *
 * <h2>Static dates:</h2>
 * <p>You can specify a static date (i.e. a constant date value).
 * Supported formats for configuring an absolute date are:
 * </p>
 * <pre>
 *   yyyy-MM-dd                -&gt; date (e.g. 2015-05-31)
 *   yyyy-MM-ddThh:mm:ss[.SSS] -&gt; date and time with optional
 *                                milliseconds (e.g. 2015-05-31T22:44:15)
 * </pre>
 *
 * <h2>Dynamic dates:</h2>
 * <P>The date string can also represent a moment in time relative to the
 * current date using the <code>TODAY</code> or <code>NOW</code> keyword,
 * optionally followed by a number of time units to add/remove.
 * <code>TODAY</code> is the current day without the hours, minutes, and
 * seconds, where as <code>NOW</code> is the current day with the hours,
 * minutes, and seconds. You can also decide whether you want the
 * current date to be fixed, producing the same date at each supplier
 * invocation (i.e., does not change after being created for the first time),
 * or whether it should be refreshed on every invocation to reflect the
 * passing of time.
 * </p>
 *
 * <h2>Time zones:</h2>
 * <p>
 * If the time zone (id or offset) is part of the formatted date string,
 * it will be honored.  If not specified, it will use the provided time zone
 * argument, or fall back to UTC if the time zone argument is {@code null}.
 * </p>
 *
 * <h2>Date format:</h2>
 * <p>
 * Date value format are either one of:
 * </p>
 * <pre>
 *   yyyy-MM-dd                -> date (e.g. 2015-05-31)
 *   yyyy-MM-ddThh:mm:ss[.SSS] -> date and time with optional
 *                                milliseconds (e.g. 2015-05-31T22:44:15)
 *   TODAY[-+]9[YMDhms][*]     -> the string "TODAY" (at 0:00:00) minus
 *                                or plus a number of years, months, days,
 *                                hours, minutes, or seconds
 *                                (e.g. 1 week ago: TODAY-7d).
 *                                * means TODAY can change from one
 *                                invocation to another to adjust to a
 *                                change of current day
 *   NOW[-+]9[YMDhms][*]       -> the string "NOW" (at current time) minus
 *                                or plus a number of years, months, days,
 *                                hours, minutes, or seconds
 *                                (e.g. 1 week ago: NOW-7d).
 *                                * means NOW changes from one invocation
 *                                to another to adjust to the current time.
 * </pre>
 */
public final class DateProviderFactory {

    private static final Pattern RELATIVE_PARTS = Pattern.compile(
            //1              23            4         5
            "^(NOW|TODAY)\\s*(([-+]{1})\\s*(\\d+)\\s*([YMDhms]{1})\\s*)?"
                    //6
                    + "(\\*?)$");

    private DateProviderFactory() {
    }

    /**
     * Create a new date supplier based on the given string. The
     * <code>ZoneId</code> is ignored for a static date, which is expected
     * to have it as part of its string representation if required.
     * Dynamic dates will default to UTC time-zone if not is supplied.
     * @param dateStr date string to parse
     * @param zoneId zone id for the date supplier, if applicable
     * @return zoned date time supplier
     */
    public static DateProvider create(
            @NonNull String dateStr, ZoneId zoneId) {
        // NOW[-+]9[YMDhms][*]
        // TODAY[-+]9[YMDhms][*]
        var d = dateStr.trim();

        var m = RELATIVE_PARTS.matcher(d);
        if (m.matches()) {
            //--- Dynamic ---
            TimeUnit unit = null;
            var amount = NumberUtils.toInt(m.group(4), -1);
            if (amount > -1) {
                if ("-".equals(m.group(3))) {
                    amount = -amount;
                }
                var unitStr = m.group(5);
                unit = TimeUnit.getTimeUnit(unitStr);
                if (unit == null) {
                    throw new ConfigurationException(
                            "Invalid time unit: " + unitStr);
                }
            }
            var fixed = !"*".equals(m.group(6));
            var today = "TODAY".equals(m.group(1));

            if (fixed) {
                return new DynamicFixedDateTimeProvider(
                        unit, amount, today, zoneId);
            }
            return new DynamicFloatingDateTimeProvider(
                    unit, amount, today, zoneId);
        }

        //--- Static ---
        String dateFormat = null;
        var valueHasZone = false;

        if (d.contains(".")) {
            dateFormat = "yyyy-MM-dd'T'HH:mm:ss.nnn";
        } else if (d.contains("T")) {
            dateFormat = "yyyy-MM-dd'T'HH:mm:ss";
        } else {
            dateFormat = "yyyy-MM-dd";
        }
        if (StringUtils.countMatches(d, "-") > 2 || d.contains("+")) {
            dateFormat += "Z";
            valueHasZone = true;
        }
        if (d.contains("[")) {
            dateFormat += "'['VV']'";
            valueHasZone = true;
        }

        var dt = ZonedDateTimeParser.builder()
                .format(dateFormat)
                .zoneId(valueHasZone ? null : zoneId)
                .build()
                .parse(d);
        return new StaticDateTimeProvider(dt);
    }

    /**
     * Static date-time, supplying the value passed in constructor.
     */
    @Data
    public static class StaticDateTimeProvider implements DateProvider {
        private final ZonedDateTime dateTime;
        private final String toString;

        public StaticDateTimeProvider(@NonNull ZonedDateTime dateTime) {
            this.dateTime = dateTime;
            toString = dateTime.format(
                    DateTimeFormatter.ofPattern(
                            "yyyy-MM-dd'T'HH:mm:ss.nnnZ'['VV']'"));
        }

        @Override
        public ZonedDateTime getDateTime() {
            return dateTime;
        }

        @Override
        public ZoneId getZoneId() {
            return dateTime.getZone();
        }

        @Override
        public String toString() {
            return toString;
        }
    }

    /**
     * Returns a supplier that dynamically generates a date-time, that
     * changes with every invocation.
     */
    @EqualsAndHashCode
    public static class DynamicFloatingDateTimeProvider
            implements DateProvider {
        private final TimeUnit unit;
        private final int amount;
        private final boolean today; // default is false == NOW
        private final ZoneId zoneId;

        public DynamicFloatingDateTimeProvider(
                TimeUnit unit, int amount, boolean today, ZoneId zoneId) {
            this.unit = unit;
            this.amount = amount;
            this.today = today;
            this.zoneId = zoneId;
        }

        @Override
        public ZonedDateTime getDateTime() {
            return dynamicDateTime(unit, amount, today, zoneId);
        }

        @Override
        public ZoneId getZoneId() {
            return zoneId;
        }

        @Override
        public String toString() {
            return dynamicToString(unit, amount, today, true);
        }
    }

    /**
     * Returns a supplier that dynamically generates a date-time, that once
     * generated, never changes for that same supplier.
     */
    @EqualsAndHashCode
    public static class DynamicFixedDateTimeProvider
            implements DateProvider {
        private final TimeUnit unit;
        private final int amount;
        private final boolean today; // default is false == NOW
        private final ZoneId zoneId;
        private final String toString;
        @EqualsAndHashCode.Exclude
        @ToString.Exclude
        private ZonedDateTime dateTime;

        public DynamicFixedDateTimeProvider(
                TimeUnit unit, int amount, boolean today, ZoneId zoneId) {
            this.unit = unit;
            this.amount = amount;
            this.today = today;
            this.zoneId = zoneId;
            toString = dynamicToString(unit, amount, today, false);
        }

        @Override
        public ZonedDateTime getDateTime() {
            if (dateTime == null) {
                dateTime = createDateTime(zoneId);
            }
            return dateTime;
        }

        @Override
        public ZoneId getZoneId() {
            return zoneId;
        }

        public synchronized ZonedDateTime createDateTime(ZoneId zoneId) {
            if (dateTime == null) {
                return dynamicDateTime(unit, amount, today, zoneId);
            }
            return dateTime;
        }

        @Override
        public String toString() {
            return toString;
        }
    }

    private static ZonedDateTime dynamicDateTime(
            TimeUnit unit, int amount, boolean today, ZoneId zoneId) {
        var dt = ZonedDateTime.now(zoneIdOrUTC(zoneId));

        if (today) {
            dt = dt.truncatedTo(ChronoUnit.DAYS);
        }
        if (unit != null) {
            dt = dt.plus(amount, unit.toTemporal());
        }
        return dt;
    }

    private static String dynamicToString(
            TimeUnit unit,
            int amount,
            boolean today,
            boolean floating) {
        var b = new StringBuilder();
        if (today) {
            b.append("TODAY");
        } else {
            b.append("NOW");
        }
        if (unit != null) {
            if (amount >= 0) {
                b.append('+');
            }
            b.append(amount);
            b.append(unit.toString());
        }
        if (floating) {
            b.append('*');
        }
        return b.toString();
    }

    private static ZoneId zoneIdOrUTC(ZoneId zoneId) {
        return zoneId == null ? ZoneOffset.UTC : zoneId;
    }

}
