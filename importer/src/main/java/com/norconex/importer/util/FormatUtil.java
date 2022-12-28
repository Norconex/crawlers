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
package com.norconex.importer.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods related to formatting.  Date formatting from string
 * uses the US English locale when the locale is not specified.
 * In addition to standard Java date format syntax, you can use
 * the string "EPOCH" (or <code>null</code>) to represent an EPOCH format.
 */
public final class FormatUtil {
//TODO consider moving to Norconex Commons Lang

    private static final Logger LOG = LoggerFactory.getLogger(FormatUtil.class);

    private FormatUtil() {
    }


    /**
     * Formats a string representation of a date, into another string date
     * format.
     * @param dateString the date to format
     * @param fromFormat source format (<code>null</code> means EPOCH)
     * @param toFormat target format (<code>null</code> means EPOCH)
     * @return formatted date string, or <code>null</code> if unable to format
     */
    public static String formatDateString(
            String dateString, String fromFormat, String toFormat) {
        return formatDateString(dateString, fromFormat, toFormat, null);
    }

    /**
     * Formats a string representation of a date, into another string date
     * format.
     * @param dateString the date to format
     * @param fromFormat source format
     * @param toFormat target format
     * @param fieldName optional field name for referencing in error messages
     * @return formatted date string
     */
    public static String formatDateString(
            String dateString, String fromFormat,
            String toFormat, String fieldName) {
        return formatDateString(
                dateString, fromFormat, null, toFormat, null, fieldName);
    }


    /**
     * Formats a string representation of a date, into another string date
     * format.
     * @param dateString the date to format
     * @param fromFormat source format
     * @param fromLocale source format locale
     * @param toFormat target format
     * @param toLocale target format locale
     * @param subjectName optional subject name to appear in error messages
     * @return formatted date string
         */
    public static String formatDateString(String dateString,
            String fromFormat, Locale fromLocale,
            String toFormat, Locale toLocale, String subjectName) {
        if (StringUtils.isBlank(dateString)) {
            return null;
        }

        //--- Parse from date ---
        Locale sourceLocale = fromLocale;
        if (sourceLocale == null) {
            sourceLocale = Locale.US;
        }
        Date date;
        if (isEpochFormat(fromFormat)) {
            // From date format is EPOCH
            long millis = NumberUtils.toLong(dateString, -1);
            if (millis == -1) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Invalid date format{}"
                            + "The date is expected to be of EPOCH format: {}",
                            formatSubjectMsg(subjectName), dateString);
                }
                return null;
            }
            date = new Date(millis);
        } else {
            // From date is custom format
            try {
                date = new SimpleDateFormat(
                        fromFormat, sourceLocale).parse(dateString);
            } catch (ParseException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Invalid date format{}",
                            formatSubjectMsg(subjectName) + e.getMessage());
                }
                return null;
            }
        }

        //--- Format to date ---
        Locale targetLocale = toLocale;
        if (targetLocale == null) {
            targetLocale = Locale.US;
        }
        String toDate;
        if (isEpochFormat(toFormat)) {
            // To date format is EPOCH
            toDate = Long.toString(date.getTime());
        } else {
            toDate = new SimpleDateFormat(
                    toFormat, targetLocale).format(date);
        }
        return toDate;
    }

    /**
     * Formats a string representation of a date, into another string date
     * format.
     * @param dateString the date to format
     * @param fromFormat source format
     * @param fromLocale source format locale
     * @param subjectName optional subject name to appear in error messages
     * @param zoneId time zone id or <code>null</code> to use system default
     *        or detected one if specified in format
     * @return a local date time or <code>null</code> if date string is
     *         <code>null</code> or could not parse.
         */
    //TODO simplify and merge some logic with formatDateSTring
    //TODO move somewhere else? Nx Commons Lang?
    //TODO Make it a builder, with option to throw exception
    //     (and/or set loglevel?)
    public static ZonedDateTime parseZonedDateTimeString(
            String dateString,
            String fromFormat,
            Locale fromLocale,
            String subjectName,
            ZoneId zoneId) {
        if (StringUtils.isBlank(dateString)) {
            return null;
        }

        //--- Parse from date ---
        Locale sourceLocale = fromLocale != null ? fromLocale : Locale.US;
        ZonedDateTime dateTime = null;
        if (isEpochFormat(fromFormat)) {
            // From date format is EPOCH
            long millis = NumberUtils.toLong(dateString, -1);
            if (millis == -1) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Invalid date format{}"
                            + "The date is expected to be of EPOCH format: {}",
                            formatSubjectMsg(subjectName), dateString);
                }
                return null;
            }

            dateTime = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(millis), safeZoneId(zoneId));
        } else {
            // From date is custom format
            try {

// If zone id is specified, we set the Zone, ignoring the one already defined (if any)
// If zone id is NOT specified (null), it means to use whatever is detected
//   in the date string if zone was provided, or default zone otherwise.


                DateTimeFormatter dtf = new DateTimeFormatterBuilder().append(
                        DateTimeFormatter.ofPattern(fromFormat, sourceLocale))
                            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                            .toFormatter();

                TemporalAccessor parsed = dtf.parseBest(dateString,
                        ZonedDateTime::from,
                        LocalDateTime::from);

                // if it's a ZonedDateTime, return it
                if (parsed instanceof ZonedDateTime) {
                    dateTime = (ZonedDateTime) parsed;
                    if (zoneId != null) {
                        dateTime = dateTime.withZoneSameLocal(zoneId);
                    }
                } else if (parsed instanceof LocalDateTime) {
                    // convert LocalDateTime to JVM default timezone
                    LocalDateTime dt = (LocalDateTime) parsed;
                    dateTime = dt.atZone(safeZoneId(zoneId));
                }

            } catch (DateTimeParseException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Invalid date format{}",
                            formatSubjectMsg(subjectName) + e.getMessage());
                }
                return null;
            }
        }

        return dateTime;
    }

    // returns true if blank or "EPOCH" (case insensitive).
    private static boolean isEpochFormat(String format) {
        return StringUtils.isBlank(format) || "EPOCH".equalsIgnoreCase(format);
    }

    private static String formatSubjectMsg(String subject) {
        String fieldMsg = ". ";
        if (StringUtils.isNotBlank(subject)) {
            fieldMsg = " for '" + subject + "'. ";
        }
        return fieldMsg;
    }

    private static ZoneId safeZoneId(ZoneId nullableZoneId) {
        return Optional.ofNullable(
                nullableZoneId).orElse(ZoneId.systemDefault()).normalized();
    }
}
