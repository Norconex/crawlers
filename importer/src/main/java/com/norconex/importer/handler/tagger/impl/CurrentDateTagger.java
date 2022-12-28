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
package com.norconex.importer.handler.tagger.impl;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Adds the current computer UTC date to the specified <code>field</code>.
 * If no <code>field</code> is provided, the date will be added to
 * <code>document.importedDate</code>.
 * </p>
 * <p>
 * The default date format is EPOCH
 * (the difference, measured in milliseconds, between the current time and
 * midnight, January 1, 1970 UTC).
 * A custom date format can be specified with the <code>format</code>
 * attribute, as per the
 * formatting options found on {@link SimpleDateFormat}.
 * </p>
 *
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 *
 * <p>Can be used both as a pre-parse or post-parse handler.</p>
 *
 * <p>It is possible to specify a locale used for formatting
 * dates. The locale is the ISO two-letter language code,
 * with an optional ISO country code, separated with an underscore
 * (e.g., "fr" for French, "fr_CA" for Canadian French). When no locale is
 * specified, the default is "en_US" (US English).</p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.CurrentDateTagger"
 *     toField="(target field)"
 *     format="(date format)"
 *     locale="(locale)"
 *     {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="CurrentDateTagger"
 *      toField="crawl_date" format="yyyy-MM-dd HH:mm" />
 * }
 * <p>
 * The above will store the current date along with hours and minutes
 * in a "crawl_date" field.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class CurrentDateTagger extends AbstractDocumentTagger {

    public static final String DEFAULT_FIELD = DocMetadata.IMPORTED_DATE;

    private String toField = DEFAULT_FIELD;
    private String format;
    private Locale locale;
    private PropertySetter onSet;

    @Override
    public void tagApplicableDocument(
            HandlerDoc doc, InputStream document, ParseState parseState)
                    throws ImporterHandlerException {
        var date = formatDate(System.currentTimeMillis());
        var finalField = toField;
        if (StringUtils.isBlank(finalField)) {
            finalField = DEFAULT_FIELD;
        }
        PropertySetter.orAppend(onSet).apply(
                doc.getMetadata(), finalField, date);
    }

    private String formatDate(long time) {
        if (StringUtils.isBlank(format)) {
            return Long.toString(time);
        }
        var safeLocale = locale;
        if (safeLocale == null) {
            safeLocale = Locale.US;
        }
        return new SimpleDateFormat(
                format, safeLocale).format(new Date(time));
    }

    /**
     * Gets the target field.
     * @return target field
     */
    public String getToField() {
        return toField;
    }
    /**
     * Sets the target field.
     * @param toField target field
     */
    public void setToField(String toField) {
        this.toField = toField;
    }

    public String getFormat() {
        return format;
    }
    public void setFormat(String toFormat) {
        format = toFormat;
    }

    /**
     * Gets the locale used for formatting.
     * @return locale
     */
    public Locale getLocale() {
        return locale;
    }
    /**
     * Sets the locale used for formatting.
     * @param locale locale
     */
    public void setLocale(Locale locale) {
        this.locale = locale;
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

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setOnSet(PropertySetter.fromXML(xml, onSet));
        setToField(xml.getString("@toField", toField));
        setFormat(xml.getString("@format", format));
        setLocale(xml.getLocale("@locale", locale));
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        PropertySetter.toXML(xml, getOnSet());
        xml.setAttribute("toField", toField);
        xml.setAttribute("format", format);
        xml.setAttribute("locale",  locale);
    }
}
