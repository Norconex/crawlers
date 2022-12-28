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

import static com.norconex.commons.lang.xml.XPathUtil.attr;

import java.io.InputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.norconex.commons.lang.Operator;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.AbstractDocumentFilter;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.FormatUtil;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
/**
 * <p>Accepts or rejects a document based on whether field values correspond
 * to a date matching supplied conditions and format.
 * If multiple values are
 * found for a field, only one of them needs to match for this filter to
 * take effect. If the value cannot be parsed to a valid date, it is
 * considered not to be matching (no exception is thrown).
 * </p>
 *
 * <h3>Metadata date field format:</h3>
 * <p>To successfully parse a date, you can specify a date format,
 * as per the formatting options found on {@link DateTimeFormatter}.
 * The default format when not specified is EPOCH (the difference, measured in
 * milliseconds, between the date and midnight, January 1, 1970).</p>
 *
 * <h3>Absolute date conditions:</h3>
 * <p>When defining a filter condition, you can specify an absolute
 * date (i.e. a constant date value) to be used for comparison.
 * Supported formats for specifying a condition date are:
 * </p>
 * <pre>
 *   yyyy-MM-dd                -&gt; date (e.g. 2015-05-31)
 *   yyyy-MM-ddThh:mm:ss[.SSS] -&gt; date and time with optional
 *                                milliseconds (e.g. 2015-05-31T22:44:15)
 * </pre>
 *
 * <h3>Relative date conditions:</h3>
 * <P>Filter conditions can also specify a moment in time relative to the
 * current date using the <code>TODAY</code> or <code>NOW</code> keyword,
 * optionally followed by a number of time units to add/remove.
 * <code>TODAY</code> is the current day without the hours, minutes, and
 * seconds, where as <code>NOW</code> is the current day with the hours,
 * minutes, and seconds. You can also decide whether you want the
 * current date to be fixed for life time of this filter (does not change
 * after being set for the first time), or whether
 * it should be refreshed on every invocation to reflect the passing of time.
 * </p>
 *
 * <h3>Time zones:</h3>
 * <p>
 * When comparing dates at a more granular level (e.g., hours, minutes,
 * seconds), it may be important to take time zones into account.
 * If the time zone (id or offset) is part of a document field date value
 * and this filter configured format supports time zones, it will be be
 * interpreted as a date in the encountered time zone.
 * </p>
 * <p>
 * In cases where you want to overwrite the value existing time zone or
 * specify one for field dates without time zones, you can do so with
 * the {@link #setDocZoneId(ZoneId)} method.
 * Explicitly setting a time zone will not "convert" a date to that time zone,
 * but will rather assume it was created in the supplied time zone.
 * </p>
 * <p>
 * When using XML configuration to define the condition dates, you can
 * specify the time zone using the <code>conditionZoneId</code> option.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.filter.impl.DateMetadataFilter"
 *     {@nx.include com.norconex.importer.handler.filter.AbstractDocumentFilter#attributes}
 *     format="(document field date format)"
 *     docZoneId="(force a time zone on evaluated fields.)"
 *     conditionZoneId="(time zone of condition dates.)">
 *
 *     {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *     <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (expression matching date fields to filter)
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
 *     <condition operator="[gt|ge|lt|le|eq]" date="(a date)" />
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="DateMetadataFilter"
 *     format="yyyy-MM-dd'T'HH:mm:ssZ"
 *     conditionZoneId="America/New_York"
 *     onMatch="include">
 *   <fieldMatcher>publish_date</fieldMatcher>
 *   <condition operator="ge" date="TODAY-7" />
 *   <condition operator="lt" date="TODAY" />
 * </handler>
 * }
 * <p>
 * The above example will only keep documents from the last
 * seven days, not including today.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@Slf4j
@EqualsAndHashCode
@ToString
@FieldNameConstants
public class DateMetadataFilter extends AbstractDocumentFilter {


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

    private final TextMatcher fieldMatcher = new TextMatcher();
    private String format;
    private final List<Condition> conditions = new ArrayList<>(2);

    private ZoneId docZoneId;
    // condition zoneId is only kept here for when we save to XML.
    private ZoneId conditionZoneId;

    public DateMetadataFilter() {}

    /**
     * Constructor.
     * @param fieldMatcher matcher for fields on which to apply date filtering
     */
    public DateMetadataFilter(TextMatcher fieldMatcher) {
        this(fieldMatcher, OnMatch.INCLUDE);
    }
    /**
     *
     * @param fieldMatcher matcher for fields on which to apply date filtering
     * @param onMatch include or exclude on match
     */
    public DateMetadataFilter(TextMatcher fieldMatcher, OnMatch onMatch) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        setOnMatch(onMatch);
    }

    /**
     * Gets the time zone id documents are considered to be.
     * @return zone id
     */
    public ZoneId getDocZoneId() {
        return docZoneId;
    }
    /**
     * Sets the time zone id documents are considered to be.
     * @param docZoneId zone id
     */
    public void setDocZoneId(ZoneId docZoneId) {
        this.docZoneId = docZoneId;
    }

    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    public String getFormat() {
        return format;
    }
    public void setFormat(String format) {
        this.format = format;
    }

    public void addCondition(Operator operator, ZonedDateTime dateTime) {
        Objects.requireNonNull(dateTime, "'dateTime' must not be null.");
        conditions.add(new Condition(
                operator, new StaticDateTimeSupplier(dateTime)));
    }
    public void addCondition(Operator operator,
            Supplier<ZonedDateTime> dateTimeSupplier) {
        Objects.requireNonNull(dateTimeSupplier,
                "'dateTimeSupplier' must not be null.");
        conditions.add(new Condition(operator, dateTimeSupplier));
    }
    public void addCondition(Condition condition) {
        Objects.requireNonNull(condition, "'condition' must not be null.");
        conditions.add(condition);
    }
    /**
     * Adds a list of conditions, appending them to the list of already
     * defined conditions in this filter (if any).
     * @param conditions list of conditions
     */
    public void addConditions(List<Condition> conditions) {
        this.conditions.addAll(conditions);
    }
    /**
     * Sets a list of conditions, overwriting any existing ones in this filter.
     * @param conditions list of conditions
     */
    public void setConditions(List<Condition> conditions) {
        CollectionUtil.setAll(this.conditions, conditions);
    }
    /**
     * Gets the list date filter conditions for this filter.
     * @return conditions
     */
    public List<Condition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }
    /**
     * Removes a condition, if it part of already defined conditions.
     * @param condition the condition to remove
     * @return <code>true</code> if the filter contained the condition
     */
    public boolean removeCondition(Condition condition) {
        return conditions.remove(condition);
    }
    /**
     * Removes all conditions from this filter.
     */
    public void removeAllConditions() {
        conditions.clear();
    }

    @Override
    protected boolean isDocumentMatched(
            HandlerDoc doc, InputStream input, ParseState parseState)
                    throws ImporterHandlerException {

        if (fieldMatcher.getPattern() == null) {
            throw new IllegalArgumentException(
                    "\"fieldMatcher\" pattern cannot be empty.");
        }
        for (Entry<String, List<String>> en :
                doc.getMetadata().matchKeys(fieldMatcher).entrySet()) {
            for (String value : en.getValue()) {
                if (meetsAllConditions(en.getKey(), value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean meetsAllConditions(String fieldName, String fieldValue) {


        var dt = FormatUtil.parseZonedDateTimeString(
                fieldValue, format, null, fieldName, docZoneId);
        if (dt == null) {
            return false;
        }
        for (Condition condition : conditions) {
            var evalResult = condition.operator.evaluate(
                    dt, condition.getDateTime());
            if (LOG.isDebugEnabled()) {
                LOG.debug("{}: {} [{}] {} = {}",
                        fieldName, fieldValue, condition.operator,
                        condition.getDateTime(), evalResult);
            }
            if (!evalResult) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void loadFilterFromXML(XML xml) {
        fieldMatcher.loadFromXML(xml.getXML(Fields.fieldMatcher));
        setFormat(xml.getString(attr(Fields.format), format));

        ZoneId dZoneId = null;
        var dZoneIdStr = xml.getString(attr(Fields.docZoneId), null);
        if (StringUtils.isNotBlank(dZoneIdStr)) {
            dZoneId = ZoneId.of(dZoneIdStr);
        }
        setDocZoneId(dZoneId);

        ZoneId cZoneId = null;
        var cZoneIdStr = xml.getString(attr(Fields.conditionZoneId), null);
        if (StringUtils.isNotBlank(cZoneIdStr)) {
            cZoneId = ZoneId.of(cZoneIdStr);
        }
        conditionZoneId = cZoneId;

        var nodes = xml.getXMLList("condition");
        for (XML node : nodes) {
            var op = node.getString("@operator", null);
            var dateStr = node.getString("@date", null);
            var isValid = true;
            if (StringUtils.isAnyBlank(op, dateStr)) {
                LOG.warn("Both \"operator\" and \"date\" must be provided.");
                isValid = false;
            } else if (Operator.of(op) == null) {
                LOG.warn("Unsupported operator: {}", op);
                isValid = false;
            }
            if (!isValid) {
                break;
            }
            conditions.add(toCondition(Operator.of(op), dateStr, cZoneId));
        }
    }

    @Override
    protected void saveFilterToXML(XML xml) {
        xml.setAttribute("format", format);
        xml.setAttribute("docZoneId", docZoneId);
        xml.setAttribute("conditionZoneId", conditionZoneId);
        for (Condition condition : conditions) {
            xml.addElement("condition")
                    .setAttribute("operator", condition.operator.toString())
                    .setAttribute("date",
                            condition.dateTimeSupplier.toString());
        }
        fieldMatcher.saveToXML(xml.addElement(Fields.fieldMatcher));
    }

    private static final Pattern RELATIVE_PARTS = Pattern.compile(
            //1              23            4         5
            "^(NOW|TODAY)\\s*(([-+]{1})\\s*(\\d+)\\s*([YMDhms]{1})\\s*)?"
            //6
           + "(\\*?)$");
    public static Condition toCondition(
            Operator operator, String dateString, ZoneId zoneId) {
        try {
            var d = dateString.trim();

            // NOW[-+]9[YMDhms][*]
            // TODAY[-+]9[YMDhms][*]
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
                    return new Condition(operator,
                            new DynamicFixedDateTimeSupplier(
                                    unit, amount, today, zoneId));
                }
                return new Condition(operator,
                        new DynamicFloatingDateTimeSupplier(
                                unit, amount, today, zoneId));
            }

            //--- Static ---
            String dateFormat = null;
            if (d.contains(".")) {
                dateFormat = "yyyy-MM-dd'T'HH:mm:ss.nnn";
            } else if (d.contains("T")) {
                dateFormat = "yyyy-MM-dd'T'HH:mm:ss";
            } else {
                dateFormat = "yyyy-MM-dd";
            }
            var dt = FormatUtil.parseZonedDateTimeString(
                    dateString, dateFormat, null, null, zoneId);
            return new Condition(operator, new StaticDateTimeSupplier(dt));
        } catch (DateTimeParseException e) {
            throw new ConfigurationException(
                    "Date parse error for value: " + dateString, e);
        }
    }

    @EqualsAndHashCode
    @ToString
    public static class Condition {
        private final Operator operator;
        private final Supplier<ZonedDateTime> dateTimeSupplier;
        public Condition(
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
        public StaticDateTimeSupplier(ZonedDateTime dateTime) {
            this.dateTime = Objects.requireNonNull(
                    dateTime, "'dateTime' must not be null.");
            toString = dateTime.format(DateTimeFormatter.ofPattern(
                    "yyyy-MM-dd'T'HH:mm:ss.nnn"));
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
        var dt = ZonedDateTime.now();
        if (zoneId != null) {
            dt = dt.withZoneSameLocal(zoneId);
        }

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
}

