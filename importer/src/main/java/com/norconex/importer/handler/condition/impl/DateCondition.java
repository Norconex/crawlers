/* Copyright 2021-2022 Norconex Inc.
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

import static com.norconex.commons.lang.Operator.EQUALS;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.norconex.commons.lang.Operator;
import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.time.ZonedDateTimeParser;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.condition.ImporterCondition;
import com.norconex.importer.parser.ParseState;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * A condition based on the date value(s) of matching
 * metadata fields given the supplied date format. If multiple values are
 * found for a field, only one of them needs to match for this condition to
 * be true.
 * If the value is not a valid date, it is considered not to be matching
 * (i.e., <code>false</code>).
 * The default operator is "eq" (equals).
 * </p>
 *
 * <h3>Single date vs range of dates:</h3>
 * <p>
 * This condition accepts zero, one, or two value matchers:
 * </p>
 * <ul>
 *   <li>
 *     <b>0:</b> Use no value matcher to simply evaluate
 *     whether the value is a date.
 *   </li>
 *   <li>
 *     <b>1:</b> Use one value matcher to evaluate if the value is
 *     lower/greater and/or the same as the specified date.
 *   </li>
 *   <li>
 *     <b>2:</b> Use two value matchers to define a date range to evaluate
 *     (both matches have to evaluate to <code>true</code>).
 *   </li>
 * </ul>
 *
 * <h3>Metadata date field format:</h3>
 * <p>To successfully parse a date, you can specify a date format,
 * as per the formatting options found on {@link DateTimeFormatter}.
 * The default format when not specified is EPOCH (the difference, measured in
 * milliseconds, between the date and midnight, January 1, 1970).</p>
 *
 * <h3>Absolute date conditions:</h3>
 * <p>When defining a date value matcher, you can specify an absolute
 * date (i.e. a constant date value) to be used for comparison.
 * Supported formats for configuring an absolute date are:
 * </p>
 * <pre>
 *   yyyy-MM-dd                -&gt; date (e.g. 2015-05-31)
 *   yyyy-MM-ddThh:mm:ss[.SSS] -&gt; date and time with optional
 *                                milliseconds (e.g. 2015-05-31T22:44:15)
 * </pre>
 *
 * <h3>Relative date conditions:</h3>
 * <P>Date value matchers can also specify a moment in time relative to the
 * current date using the <code>TODAY</code> or <code>NOW</code> keyword,
 * optionally followed by a number of time units to add/remove.
 * <code>TODAY</code> is the current day without the hours, minutes, and
 * seconds, where as <code>NOW</code> is the current day with the hours,
 * minutes, and seconds. You can also decide whether you want the
 * current date to be fixed for the lifetime of this condition (does not change
 * after being set for the first time), or whether
 * it should be refreshed on every invocation to reflect the passing of time.
 * </p>
 *
 * <h3>Time zones:</h3>
 * <p>
 * When comparing dates at a more granular level (e.g., hours, minutes,
 * seconds), it may be important to take time zones into account.
 * If the time zone (id or offset) is part of a document field date value
 * and this filter configured format supports time zones, it will respect
 * the time zone in the encountered time zone.
 * </p>
 * <p>
 * In cases where you want to specify the time zone for values
 * without one, you can do so with
 * the {@link #setDocZoneId(ZoneId)} method.
 * Explicitly setting a document time zone that way has no effect
 * if the date already defines its own zone.
 * The default time zone when none is specified is UTC.
 * </p>
 * <p>
 * When using XML configuration to define the condition dates, you can
 * specify the time zone using the <code>conditionZoneId</code> option.
 * </p>
 *
 * {@nx.include com.norconex.commons.lang.Operator#operators}
 *
 * {@nx.xml.usage
 * <condition class="com.norconex.importer.handler.condition.impl.DateCondition"
 *     format="(document field date format)"
 *     docZoneId="(force a time zone on evaluated fields)"
 *     conditionZoneId="(time zone of condition dates when not specified)">
 *
 *
 *     <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (expression matching date fields to evaluate)
 *     </fieldMatcher>
 *
 *     <!-- Use one or two (for ranges) conditions where:
 *
 *       Possible operators are:
 *
 *         gt -> greater than
 *         ge -> greater equal
 *         lt -> lower than
 *         le -> lower equal
 *         eq -> equals
 *
 *       Condition date value format are either one of:
 *
 *         yyyy-MM-dd                -> date (e.g. 2015-05-31)
 *         yyyy-MM-ddThh:mm:ss[.SSS] -> date and time with optional
 *                                      milliseconds (e.g. 2015-05-31T22:44:15)
 *         TODAY[-+]9[YMDhms][*]     -> the string "TODAY" (at 0:00:00) minus
 *                                      or plus a number of years, months, days,
 *                                      hours, minutes, or seconds
 *                                      (e.g. 1 week ago: TODAY-7d).
 *                                      * means TODAY can change from one
 *                                      invocation to another to adjust to a
 *                                      change of current day
 *         NOW[-+]9[YMDhms][*]       -> the string "NOW" (at current time) minus
 *                                      or plus a number of years, months, days,
 *                                      hours, minutes, or seconds
 *                                      (e.g. 1 week ago: NOW-7d).
 *                                      * means NOW changes from one invocation
 *                                      to another to adjust to the current time.
 *    -->
 *
 *     <valueMatcher operator="[gt|ge|lt|le|eq]" date="(a date)" />
 *
 * </condition>
 * }
 *
 * {@nx.xml.example
 * <condition class="DateCondition"
 *     format="yyyy-MM-dd'T'HH:mm:ssZ"
 *     conditionZoneId="America/New_York">
 *   <fieldMatcher>publish_date</fieldMatcher>
 *   <valueMatcher operator="ge" date="TODAY-7" />
 *   <valueMatcher operator="lt" date="TODAY" />
 * </condition>
 * }
 * <p>
 * The above example will only keep documents from the last
 * seven days, not including today.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@Data
@FieldNameConstants
@Slf4j
public class DateCondition implements ImporterCondition, XMLConfigurable {

    public enum TimeUnit {
        YEAR(ChronoUnit.YEARS, "Y"),
        MONTH(ChronoUnit.MONTHS, "M"),
        DAY(ChronoUnit.DAYS, "D"),
        HOUR(ChronoUnit.HOURS, "h"),
        MINUTE(ChronoUnit.MINUTES, "m"),
        SECOND(ChronoUnit.SECONDS, "s");
        private final TemporalUnit temporalUnit;
        private final String abbr;
        TimeUnit(TemporalUnit temporalUnit, String abbr) {
            this.temporalUnit = temporalUnit;
            this.abbr = abbr;
        }
        public TemporalUnit toTemporal() {
            return temporalUnit;
        }
        @Override
        public String toString() {
            return abbr;
        }
        public static TimeUnit getTimeUnit(String unit) {
            if (StringUtils.isBlank(unit)) {
                return null;
            }
            for (TimeUnit tu : TimeUnit.values()) {
                if (tu.abbr.equalsIgnoreCase(unit)) {
                    return tu;
                }
            }
            return null;
        }
    }

    /**
     * Field name(s) text matcher.
     * @param fieldMatcher field matcher
     * @return field matcher
     */
    private final TextMatcher fieldMatcher = new TextMatcher();

    /**
     * Value matcher for a date, or the begining of a date range
     * (if an end date value matcher is also supplied).
     * @param valueMatcher date matcher
     * @return date matcher
     */
    private ValueMatcher valueMatcher;

    /**
     * Value matcher for then end of a date range. Only set when dealing
     * with date ranges.
     * @param valueMatcherRangeEnd end of range date matcher
     * @return end of range date matcher
     */
    private ValueMatcher valueMatcherRangeEnd;

    /**
     * The format of a document date field value (see class documentation).
     * @param format date format
     * @return date format
     */
    private String format;

    /**
     * Time zone id to use for dates associated with a document when
     * evaluating date conditions.
     * @param docZoneId document zone id
     * @return document zone id
     */
    private ZoneId docZoneId;

    private ZoneId conditionZoneId;

    public DateCondition() {
    }
    public DateCondition(TextMatcher fieldMatcher) {
        this(fieldMatcher, null, null);
    }
    public DateCondition(
            TextMatcher fieldMatcher, ValueMatcher valueMatcher) {
        this(fieldMatcher, valueMatcher, null);
    }
    public DateCondition(
            TextMatcher fieldMatcher,
            ValueMatcher rangeStart,
            ValueMatcher rangeEnd) {
        setFieldMatcher(fieldMatcher);
        valueMatcher = rangeStart;
        valueMatcherRangeEnd = rangeEnd;
    }

    /**
     * Sets the text matcher of field names. Copies it.
     * @param fieldMatcher text matcher
     */
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    @Override
    public boolean testDocument(
            HandlerDoc doc, InputStream input, ParseState parseState)
                    throws ImporterHandlerException {
        if (fieldMatcher.getPattern() == null) {
            throw new IllegalArgumentException(
                    "\"fieldMatcher\" pattern cannot be empty.");
        }
        for (Entry<String, List<String>> en :
                doc.getMetadata().matchKeys(fieldMatcher).entrySet()) {
            for (String value : en.getValue()) {
                if (matches(valueMatcher, en.getKey(), value)
                        && matches(valueMatcherRangeEnd, en.getKey(), value)) {
                    return true;
                }
            }
        }
        return false;
    }
    private boolean matches(
            ValueMatcher matcher, String fieldName, String fieldValue) {
        if (matcher == null) {
            return true;
        }

        var dt = ZonedDateTimeParser.builder()
                .format(format)
                .zoneId(docZoneId)
                .build()
                .parse(fieldValue);
        if (dt == null) {
            return false;
        }

        // if the date obtained by the supplier (the date value or logic
        // configured) starts with TODAY, we truncate that date to
        // ensure we are comparing apples to apples. Else, one must ensure
        // the date format matches for proper comparisons.
        if (StringUtils.startsWithIgnoreCase(
                matcher.getDateTimeSupplier().toString(), "today")) {
            dt = dt.truncatedTo(ChronoUnit.DAYS);
        }

        var op = defaultIfNull(matcher.operator, EQUALS);
        var evalResult = op.evaluate(
                dt.toInstant(), matcher.getDateTime().toInstant());
        LOG.debug("{}: {} [{}] {} = {}",
                fieldName, fieldValue, op, matcher.getDateTime(), evalResult);
        return evalResult;
    }

    @Override
    public void loadFromXML(XML xml) {
        fieldMatcher.loadFromXML(xml.getXML("fieldMatcher"));
        setFormat(xml.getString("@format", format));

        ZoneId dZoneId = null;
        var dZoneIdStr = xml.getString("@docZoneId", null);
        if (StringUtils.isNotBlank(dZoneIdStr)) {
            dZoneId = ZoneId.of(dZoneIdStr);
        }
        setDocZoneId(dZoneId);

        ZoneId cZoneId = null;
        var cZoneIdStr = xml.getString("@conditionZoneId", null);
        if (StringUtils.isNotBlank(cZoneIdStr)) {
            cZoneId = ZoneId.of(cZoneIdStr);
        }
        conditionZoneId = cZoneId;

        var nodes = xml.getXMLList(Fields.valueMatcher);
        if (!nodes.isEmpty()) {
            setValueMatcher(toValueMatcher(nodes.get(0)));
        }
        if (nodes.size() >= 2) {
            setValueMatcherRangeEnd(toValueMatcher(nodes.get(1)));
        }
    }

    private ValueMatcher toValueMatcher(XML xml) {
        var operator = Operator.of(
                xml.getString("@operator", EQUALS.toString()));
        if (operator == null) {
            throw new IllegalArgumentException(
                    "Unsupported operator: " + xml.getString("@operator"));
        }
        var date = StringUtils.trimToNull(xml.getString("@date", null));
        try {
            return new ValueMatcher(operator, toDateTimeSupplier(date));
        } catch (DateTimeParseException e) {
            throw new ConfigurationException(
                    "Date parse error for value: " + date, e);
        }
    }

    private static final Pattern RELATIVE_PARTS = Pattern.compile(
            //1              23            4         5
            "^(NOW|TODAY)\\s*(([-+]{1})\\s*(\\d+)\\s*([YMDhms]{1})\\s*)?"
            //6
           + "(\\*?)$");
    private Supplier<ZonedDateTime> toDateTimeSupplier(
            @NonNull String dateStr) {
        // NOW[-+]9[YMDhms][*]
        // TODAY[-+]9[YMDhms][*]
        var d = dateStr.trim();
        
        var m = RELATIVE_PARTS.matcher(d);
        if (m.matches()) {
            //--- Dynamic ---
            TimeUnit unit = null;
            var amount = NumberUtils.toInt(m.group(4), -1);
            if (amount > -1) {
                if  ("-".equals(m.group(3))) {
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
                return new DynamicFixedDateTimeSupplier(
                        unit, amount, today, conditionZoneId);
            }
            return new DynamicFloatingDateTimeSupplier(
                    unit, amount, today, conditionZoneId);
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
                .zoneId(valueHasZone ? null : conditionZoneId)
                .build()
                .parse(d);
        return new StaticDateTimeSupplier(dt);
    }

    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("format", format);
        xml.setAttribute("docZoneId", docZoneId);
        xml.setAttribute("conditionZoneId", conditionZoneId);
        fieldMatcher.saveToXML(xml.addElement("fieldMatcher"));
        if (valueMatcher != null) {
            xml.addElement(Fields.valueMatcher)
                    .setAttribute("operator", valueMatcher.operator)
                    .setAttribute("date", valueMatcher.dateTimeSupplier);
        }
        if (valueMatcherRangeEnd != null) {
            // end tag name is same as start tag name in XML
            xml.addElement(Fields.valueMatcher)
                    .setAttribute("operator", valueMatcherRangeEnd.operator)
                    .setAttribute("date",
                            valueMatcherRangeEnd.dateTimeSupplier);
        }
    }

    @EqualsAndHashCode
    @ToString
    public static class ValueMatcher {
        private final Operator operator;
        private final Supplier<ZonedDateTime> dateTimeSupplier;
        public ValueMatcher(
                Operator operator,
                Supplier<ZonedDateTime> dateTimeSupplier) {
            this.operator = operator;
            this.dateTimeSupplier = dateTimeSupplier;
        }
        public ZonedDateTime getDateTime() {
            return dateTimeSupplier.get();
        }
        protected Supplier<ZonedDateTime> getDateTimeSupplier() {
            return dateTimeSupplier;
        }
    }

    // Static local date, assumed to be of the zone Id supplied
    // (the ZoneId argument is ignored).
    @EqualsAndHashCode
    public static class StaticDateTimeSupplier
            implements Supplier<ZonedDateTime> {
        private final ZonedDateTime dateTime;
        private final String toString;
        public StaticDateTimeSupplier(@NonNull ZonedDateTime dateTime) {
            this.dateTime = dateTime;
            toString = dateTime.format(DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd'T'HH:mm:ss.nnnZ'['VV']'"));
        }
        @Override
        public ZonedDateTime get() {
            return dateTime;
        }
        @Override
        public String toString() {
            return toString;
        }
    }

    // Dynamically generated date time, that changes with every invocation.
    @EqualsAndHashCode
    public static class DynamicFloatingDateTimeSupplier
            implements Supplier<ZonedDateTime> {
        private final TimeUnit unit;
        private final int amount;
        private final boolean today; // default is false == NOW
        private final ZoneId zoneId;
        public DynamicFloatingDateTimeSupplier(
                TimeUnit unit, int amount, boolean today, ZoneId zoneId) {
            this.unit = unit;
            this.amount = amount;
            this.today = today;
            this.zoneId = zoneId;
        }
        @Override
        public ZonedDateTime get() {
            return dynamicDateTime(unit, amount, today, zoneId);
        }
        @Override
        public String toString() {
            return dynamicToString(unit, amount, today, true);
        }
    }

    // Dynamically generated date time, that once generated, never changes
    @EqualsAndHashCode
    public static class DynamicFixedDateTimeSupplier
            implements Supplier<ZonedDateTime> {
        private final TimeUnit unit;
        private final int amount;
        private final boolean today; // default is false == NOW
        private final ZoneId zoneId;
        private final String toString;
        @EqualsAndHashCode.Exclude
        @ToString.Exclude        
        private ZonedDateTime dateTime;
        public DynamicFixedDateTimeSupplier(
                TimeUnit unit, int amount, boolean today, ZoneId zoneId) {
            this.unit = unit;
            this.amount = amount;
            this.today = today;
            this.zoneId = zoneId;
            toString = dynamicToString(unit, amount, today, false);
        }
        @Override
        public ZonedDateTime get() {
            if (dateTime == null) {
                dateTime = createDateTime(zoneId);
            }
            return dateTime;
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
