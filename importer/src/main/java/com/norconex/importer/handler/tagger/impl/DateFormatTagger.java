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

import static com.norconex.commons.lang.xml.XPathUtil.attr;
import static java.util.Optional.ofNullable;

import java.io.InputStream;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.time.ZonedDateTimeParser;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.parser.ParseState;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Formats a date from any given format to a format of choice, as per the
 * formatting options found on {@link ZonedDateTimeParser} with the addition
 * of the format "EPOCH", which represents the difference, measured in
 * milliseconds, between the date and midnight, January 1, 1970.
 * The default format
 * for <code>fromFormat</code> or <code>toFormat</code> when not specified
 * is EPOCH.</p>
 *
 * <p>When omitting the <code>toField</code>, the value will replace the one
 * in the same field.</p>
 *
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior
 * with {@link #setOnSet(PropertySetter)}.
 * </p>
 *
 * <p>Can be used both as a pre-parse or post-parse handler.</p>
 *
 * <p>It is possible to specify a locale used for parsing and formatting dates.
 * The locale is the ISO two-letter language code, with an optional
 * ISO country code, separated with an underscore (e.g., "fr" for French,
 * "fr_CA" for Canadian French). When no locale is specified, the default is
 * English.</p>
 *
 * <p>
 * Multiple <code>fromFormat</code> values can be specified. Each formats will
 * be tried in the order provided and the first format that succeed in
 * parsing a date will be used.
 * A date will be considered "bad" only if none of the formats could parse the
 * date.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.DateFormatTagger"
 *     fromField="(from field)" toField="(to field)"
 *     fromLocale="(locale)"    toLocale="(locale)"
 *     toFormat="(date format)"
 *     keepBadDates="(false|true)"
 *     {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple "fromFormat" tags allowed (only one needs to match) -->
 *   <fromFormat>(date format)</fromFormat>
 * </handler>
 * }
 *
 * {@nx.xml.example
 *  <handler class="DateFormatTagger"
 *          fromField="Last-Modified"
 *          toField="solr_date"
 *          toFormat="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" >
 *      <fromFormat>EEE, dd MMM yyyy HH:mm:ss zzz</fromFormat>
 *      <fromFormat>EPOCH</fromFormat>
 *  </handler>
 * }
 * <p>
 * The following converts a date that is sometimes obtained from the
 * HTTP header "Last-Modified" and sometimes is an EPOCH date,
 * into an Apache Solr date format:
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
@Slf4j
@FieldNameConstants
public class DateFormatTagger extends AbstractDocumentTagger {

    private String fromField;
    private String toField;
    private final List<String> fromFormats = new ArrayList<>();
    private String toFormat;
    /**
     * The locale used for parsing the source date.
     * @param fromLocale locale
     * @return locale
     */
    private Locale fromLocale;
    /**
     * The locale used for formatting the target date.
     * @param toLocale locale
     * @return locale
     */
    private Locale toLocale;
    private boolean keepBadDates;
    /**
     * The property setter to use when a value is set.
     * @param onSet property setter
     * @return property setter
     */
    private PropertySetter onSet;

    @Override
    public void tagApplicableDocument(
            HandlerDoc doc, InputStream document, ParseState parseState)
                    throws ImporterHandlerException {
        validateArguments();

        var fromDates = doc.getMetadata().getStrings(fromField);
        List<String> toDates = new ArrayList<>(fromDates.size());
        for (String fromDate : fromDates) {
            var toDate = formatDate(fromDate);
            if (StringUtils.isNotBlank(toDate)) {
                toDates.add(toDate);
            } else if (keepBadDates) {
                toDates.add(fromDate);
            }
        }

        if (StringUtils.isBlank(toField)) {
            PropertySetter.REPLACE.apply(doc.getMetadata(), fromField, toDates);
        } else {
            PropertySetter.orAppend(onSet).apply(
                    doc.getMetadata(), toField, toDates);
        }
    }

    private String formatDate(String fromDate) {
        List<String> formats = new ArrayList<>();
        if (fromFormats.isEmpty()) {
            formats.add("EPOCH");
        } else {
            formats.addAll(fromFormats);
        }
        for (String fromFormat : formats) {
            try {
                var fromZdt = ZonedDateTimeParser.builder()
                        .format(nullIfEpoch(fromFormat))
                        .locale(ofNullable(fromLocale).orElse(Locale.ENGLISH))
                        .build()
                        .parse(fromDate);
                if (nullIfEpoch(toFormat) == null) {
                    return Long.toString(fromZdt.toInstant().toEpochMilli());
                }
                var toDate =
                        fromZdt.format(DateTimeFormatter
                        .ofPattern(toFormat)
                        .localizedBy(
                                ofNullable(toLocale).orElse(Locale.ENGLISH)));
                if (StringUtils.isNotBlank(toDate)) {
                    return toDate;
                }
            } catch (DateTimeException e) {
                LOG.debug("Could not parse date '{}' with format '{}' "
                        + "and locale {}.", fromDate, fromFormat, fromLocale);
            }
        }
        return null;
    }

    /**
     * Gets the source date formats to match.
     * @return source date formats
     */
    public List<String> getFromFormats() {
        return fromFormats;
    }
    /**
     * Sets the source date formats to match.
     * @param fromFormats source date formats
     */
    public void setFromFormats(String... fromFormats) {
        setFromFormats(Arrays.asList(fromFormats));
    }
    /**
     * Sets the source date formats to match.
     * @param fromFormats source date formats
     */
    public void setFromFormats(List<String> fromFormats) {
        CollectionUtil.setAll(this.fromFormats, fromFormats);
    }

    private void validateArguments() {
        if (StringUtils.isBlank(fromField)) {
            throw new IllegalArgumentException(
                    "\"fromField\" cannot be empty.");
        }
        if (StringUtils.isBlank(fromField) && StringUtils.isBlank(toField)) {
            throw new IllegalArgumentException(
                    "One of \"fromField\" or \"toField\" is required.");
        }
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        fromField = xml.getString(attr(Fields.fromField), fromField);
        toField = xml.getString(attr(Fields.toField), toField);
        toFormat = xml.getString(attr(Fields.toFormat), toFormat);
        keepBadDates = xml.getBoolean(attr(Fields.keepBadDates), keepBadDates);
        fromLocale = xml.getLocale(attr(Fields.fromLocale), fromLocale);
        toLocale = xml.getLocale(attr(Fields.toLocale), toLocale);
        setFromFormats(xml.getStringList("fromFormat", fromFormats));
        setOnSet(PropertySetter.fromXML(xml, null));
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute(Fields.fromField, fromField);
        xml.setAttribute(Fields.toField, toField);
        xml.setAttribute(Fields.toFormat, toFormat);
        PropertySetter.toXML(xml, getOnSet());
        xml.setAttribute(Fields.keepBadDates, keepBadDates);
        if (fromLocale != null) {
            xml.setAttribute(Fields.fromLocale, fromLocale);
        }
        if (toLocale != null) {
            xml.setAttribute(Fields.toLocale, toLocale);
        }
        xml.addElementList("fromFormat", fromFormats);
    }

    private static String nullIfEpoch(String format) {
        return "EPOCH".equalsIgnoreCase(StringUtils.trimToEmpty(format))
                ? null : format;
    }
}
