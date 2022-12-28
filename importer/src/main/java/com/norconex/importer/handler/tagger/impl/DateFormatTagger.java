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

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.FormatUtil;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Formats a date from any given format to a format of choice, as per the
 * formatting options found on {@link SimpleDateFormat} with the exception
 * of the string "EPOCH" which represents the difference, measured in
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
 * "en_US" (US English).</p>
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
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class DateFormatTagger extends AbstractDocumentTagger {

    private String fromField;
    private String toField;
    private final List<String> fromFormats = new ArrayList<>();
    private String toFormat;
    private Locale fromLocale;
    private Locale toLocale;
    private boolean keepBadDates;
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
            var toDate = FormatUtil.formatDateString(
                    fromDate, fromFormat, fromLocale,
                    toFormat, toLocale, fromField);
            if (StringUtils.isNotBlank(toDate)) {
                return toDate;
            }
        }
        return null;
    }


    public String getFromField() {
        return fromField;
    }
    public void setFromField(String fromField) {
        this.fromField = fromField;
    }

    public String getToField() {
        return toField;
    }
    public void setToField(String toField) {
        this.toField = toField;
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

    public String getToFormat() {
        return toFormat;
    }
    public void setToFormat(String toFormat) {
        this.toFormat = toFormat;
    }

    /**
     * Gets the property setter to use when a value is set.
     * @return property setter
         */
    public PropertySetter getOnSet() {
        return onSet;
    }
    /**
     * Sets the property setter to use when a value is set.
     * @param onSet property setter
         */
    public void setOnSet(PropertySetter onSet) {
        this.onSet = onSet;
    }

    public boolean isKeepBadDates() {
        return keepBadDates;
    }
    public void setKeepBadDates(boolean keepBadDates) {
        this.keepBadDates = keepBadDates;
    }

    /**
     * Gets the locale used for parsing the source date.
     * @return locale
         */
    public Locale getFromLocale() {
        return fromLocale;
    }
    /**
     * Sets the locale used for parsing the source date.
     * @param fromLocale locale
         */
    public void setFromLocale(Locale fromLocale) {
        this.fromLocale = fromLocale;
    }

    /**
     * Gets the locale used for formatting the target date.
     * @return locale
         */
    public Locale getToLocale() {
        return toLocale;
    }
    /**
     * Sets the locale used for formatting the source date.
     * @param toLocale locale
         */
    public void setToLocale(Locale toLocale) {
        this.toLocale = toLocale;
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
        fromField = xml.getString("@fromField", fromField);
        toField = xml.getString("@toField", toField);
        toFormat = xml.getString("@toFormat", toFormat);
        keepBadDates = xml.getBoolean("@keepBadDates", keepBadDates);
        fromLocale = xml.getLocale("@fromLocale", fromLocale);
        toLocale = xml.getLocale("@toLocale", toLocale);
        setFromFormats(xml.getStringList("fromFormat", fromFormats));
        setOnSet(PropertySetter.fromXML(xml, null));
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("fromField", fromField);
        xml.setAttribute("toField", toField);
        xml.setAttribute("toFormat", toFormat);
        PropertySetter.toXML(xml, getOnSet());
        xml.setAttribute("keepBadDates", keepBadDates);
        if (fromLocale != null) {
            xml.setAttribute("fromLocale", fromLocale);
        }
        if (toLocale != null) {
            xml.setAttribute("toLocale", toLocale);
        }
        xml.addElementList("fromFormat", fromFormats);
    }
}
