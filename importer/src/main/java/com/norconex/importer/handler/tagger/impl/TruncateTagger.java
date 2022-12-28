/* Copyright 2017-2022 Norconex Inc.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.StringUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Truncates a <code>fromField</code> value(s) and optionally replace truncated
 * portion by a hash value to help ensure uniqueness (not 100% guaranteed to
 * be collision-free).  If the field to truncate has multiple values, all
 * values will be subject to truncation. You can store the value(s), truncated
 * or not, in another target field.
 * </p>
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 * <p>
 * The <code>maxLength</code> is guaranteed to be respected. This means any
 * appended hash code and suffix will fit within the <code>maxLength</code>.
 * </p>
 * <p>
 * Can be used both as a pre-parse or post-parse handler.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.TruncateTagger"
 *     maxLength="(maximum length)"
 *     toField="(optional target field where to store the truncated value)"
 *     {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}
 *     appendHash="[false|true]"
 *     suffix="(value to append after truncation. Goes before hash if one.)">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (one or more matching fields to have their values truncated)
 *   </fieldMatcher>

 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="TruncateTagger"
 *     maxLength="50"
 *     appendHash="true"
 *     suffix="!">
 *   <fieldMatcher>myField</fieldMatcher>
 * </handler>
 * }
 *
 * <p>
 * Assuming this "myField" value...
 * </p>
 * <pre>    Please truncate me before you start thinking I am too long.</pre>
 * <p>
 * ...the above example will truncate it to...
 * </p>
 * <pre>    Please truncate me before you start thi!0996700004</pre>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
@Slf4j
public class TruncateTagger extends AbstractDocumentTagger {

    private final TextMatcher fieldMatcher = new TextMatcher();
    private int maxLength;
    private String toField;
    private PropertySetter onSet;
    private boolean appendHash;
    private String suffix;

    public TruncateTagger() {}
    /**
     * Constructor.
     * @param fieldMatcher field matcher
     * @param maxLength truncation length
     */
    public TruncateTagger(TextMatcher fieldMatcher, int maxLength) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        this.maxLength = maxLength;
    }

    public String getToField() {
        return toField;
    }
    public void setToField(String keepToField) {
        toField = keepToField;
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

    public boolean isAppendHash() {
        return appendHash;
    }
    public void setAppendHash(boolean appendHash) {
        this.appendHash = appendHash;
    }
    public String getSuffix() {
        return suffix;
    }
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
    public int getMaxLength() {
        return maxLength;
    }
    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }
    /**
     * Gets field matcher for fields to truncate.
     * @return field matcher
     */
    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }
    /**
     * Sets the field matcher for fields to truncate.
     * @param fieldMatcher field matcher
     */
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    @Override
    public void tagApplicableDocument(
            HandlerDoc doc, InputStream document, ParseState parseState)
                    throws ImporterHandlerException {

        List<String> allTargetValues = new ArrayList<>();
        for (Entry<String, List<String>> en :
                doc.getMetadata().matchKeys(fieldMatcher).entrySet()) {
            var fromField = en.getKey();
            var sourceValues = en.getValue();
            List<String> targetValues = new ArrayList<>();
            for (String sourceValue : sourceValues) {
                var truncValue = truncate(sourceValue);
                targetValues.add(truncValue);
                if (LOG.isDebugEnabled()
                        && !Objects.equals(truncValue, sourceValue)) {
                    LOG.debug("\"{}\" value truncated to \"{}\".",
                            fromField, truncValue);
                }
            }

            // toField is blank, we overwrite the source and do not
            // carry values further.
            if (StringUtils.isBlank(getToField())) {
                // overwrite source field
                PropertySetter.REPLACE.apply(
                        doc.getMetadata(), fromField, targetValues);
            } else {
                allTargetValues.addAll(targetValues);
            }
        }
        if (StringUtils.isNotBlank(getToField())) {
            // set on target field
            PropertySetter.orAppend(getOnSet()).apply(
                    doc.getMetadata(), getToField(), allTargetValues);
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        if (isAppendHash()) {
            return StringUtil.truncateWithHash(
                    value, getMaxLength(), getSuffix());
        }
        if (StringUtils.isNotEmpty(getSuffix())) {
            return StringUtils.abbreviate(value, getSuffix(), getMaxLength());
        }
        return StringUtils.truncate(value, getMaxLength());
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        appendHash = xml.getBoolean("@appendHash", appendHash);
        suffix = xml.getString("@suffix", suffix);
        toField = xml.getString("@toField", toField);
        setOnSet(PropertySetter.fromXML(xml, onSet));
        maxLength = xml.getInteger("@maxLength", maxLength);
        fieldMatcher.loadFromXML(xml.getXML("fieldMatcher"));
    }
    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("appendHash", appendHash);
        xml.setAttribute("suffix", suffix);
        xml.setAttribute("toField", toField);
        xml.setAttribute("maxLength", maxLength);
        PropertySetter.toXML(xml, onSet);
        fieldMatcher.saveToXML(xml.addElement("fieldMatcher"));
    }
}
