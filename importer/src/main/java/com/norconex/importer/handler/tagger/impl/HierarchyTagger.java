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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Given a separator, split a field string into multiple segments
 * representing each node of a hierarchical branch. This is useful
 * when faceting, to find out how many documents fall under each
 * node of a hierarchy. For example, take this hierarchical string:</p>
 * <pre>
 *   /vegetable/potato/sweet
 * </pre>
 * <p>We specify a slash (/) separator and it will produce the following entries
 * in the specified document metadata field:</p>
 *
 * <pre>
 *   /vegetable
 *   /vegetable/potato
 *   /vegetable/potato/sweet
 * </pre>
 * <p>
 * If no target field is specified (<code>toField</code>) the
 * source field (<code>fromField</code>) will be used to store the resulting
 * values. The same applies to the source and target hierarchy separators
 * (<code>fromSeparator</code> and <code>toSeparator</code>).
 * </p>
 * <p>
 * You can "keepEmptySegments", as well as specify
 * whether the "fromSeparator" is a regular expression. When using regular
 * expression without a "toSeparator", the text matching the expression is
 * kept as is and thus can be different for each segment.
 * </p>
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 * <p>
 * Can be used both as a pre-parse or post-parse handler.
 * </p>
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.HierarchyTagger">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple hierarchy tags allowed -->
 *   <hierarchy fromField="(from field)"
 *       toField="(optional to field)"
 *       fromSeparator="(original separator)"
 *       toSeparator="(optional new separator)"
 *       {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}
 *       regex="[false|true]"
 *       keepEmptySegments="[false|true]" />
 * </handler>
 * }
 *
 * {@nx.xml.example
 *  <handler class="HierarchyTagger">
 *      <hierarchy fromField="vegetable" toField="vegetableHierarchy"
 *                 fromSeparator="/"/>
 *  </handler>
 * }
 * <p>
 * The above will expand a slash-separated vegetable hierarchy found in a
 * "vegetable" field into a "vegetableHierarchy" field.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class HierarchyTagger extends AbstractDocumentTagger {

    private final List<HierarchyDetails> list = new ArrayList<>();

    @Override
    public void tagApplicableDocument(
            HandlerDoc doc, InputStream document, ParseState parseState)
                    throws ImporterHandlerException {

        for (HierarchyDetails details : list) {
            breakSegments(doc.getMetadata(), details);
        }
    }

    private void breakSegments(
            Properties metadata, HierarchyDetails details) {

        Pattern delim;
        if (details.regex) {
            delim = Pattern.compile(details.fromSeparator);
        } else {
            delim = Pattern.compile(Pattern.quote(details.fromSeparator));
        }

        List<String> paths = new ArrayList<>();
        for (String value : metadata.getStrings(details.fromField)) {
            if (value == null) {
                continue;
            }

            List<Object> segments = new ArrayList<>();
            var prevMatch = 0;
            var m = delim.matcher(value);
            while (m.find()) {
                var delimStart = m.start();
                if (prevMatch != delimStart) {
                    segments.add(value.substring(prevMatch, delimStart));
                }
                prevMatch = m.end();

                var sep = m.group();
                if (StringUtils.isNotEmpty(details.toSeparator)) {
                    sep = details.toSeparator;
                }
                segments.add(new Separator(sep));
            }
            if (value.length() > prevMatch) {
                segments.add(value.substring(prevMatch));
            }

            // if not keeping empty segments, keep last of a series
            // (iterating in reverse to help do so)
            var prevIsSep = false;
            if (!details.keepEmptySegments) {
                var iter =
                        segments.listIterator(segments.size());
                while(iter.hasPrevious()) {
                    var seg = iter.previous();
                    if (seg instanceof Separator) {
                        if (prevIsSep) {
                            iter.remove();
                        }
                        prevIsSep = true;
                    } else {
                        prevIsSep = false;
                    }
                }
            }

            prevIsSep = false;
            var b = new StringBuilder();
            for (Object seg : segments) {
                if (seg instanceof Separator) {
                    if (prevIsSep) {
                        paths.add(b.toString());
                    }
                    b.append(seg);
                    prevIsSep = true;
                } else {
                    b.append(seg);
                    prevIsSep = false;
                    paths.add(b.toString());
                }
            }
        }

        if (StringUtils.isNotBlank(details.toField)) {
            // set on target field
            PropertySetter.orAppend(details.onSet).apply(
                    metadata, details.toField, paths);
        } else {
            // overwrite source field
            PropertySetter.REPLACE.apply(metadata, details.fromField, paths);
        }
    }

    /**
     * Adds hierarchy instructions.
     * @param details hierarchy details
     */
    public void addHierarcyDetails(HierarchyDetails details) {
        if (details == null || StringUtils.isAnyBlank(
                details.fromField, details.fromSeparator)) {
            return;
        }
        list.add(details);
    }

    public List<HierarchyDetails> getHierarchyDetails() {
        return list;
    }


    @Override
    protected void loadHandlerFromXML(XML xml) {
        for (XML node : xml.getXMLList("hierarchy")) {
            node.checkDeprecated("@overwrite", "onSet", true);
            var hd = new HierarchyDetails(
                    node.getString("@fromField", null),
                    node.getString("@toField", null),
                    node.getString("@fromSeparator", null),
                    node.getString("@toSeparator", null));
            hd.setOnSet(PropertySetter.fromXML(node, null));
            hd.setKeepEmptySegments(
                    node.getBoolean("@keepEmptySegments", false));
            hd.setRegex(node.getBoolean("@regex", false));
            addHierarcyDetails(hd);
        }
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        for (HierarchyDetails hd : list) {
            var node = xml.addElement("hierarchy")
                    .setAttribute("fromField", hd.fromField)
                    .setAttribute("toField", hd.toField)
                    .setAttribute("fromSeparator", hd.fromSeparator)
                    .setAttribute("toSeparator", hd.toSeparator)
                    .setAttribute("keepEmptySegments", hd.keepEmptySegments)
                    .setAttribute("regex", hd.regex);
            PropertySetter.toXML(node, hd.getOnSet());
        }
    }

    @EqualsAndHashCode
    private static class Separator {
        private final String sep;
        public Separator(String sep) {
            this.sep = sep;
        }
        @Override
        public String toString() {
            return sep;
        }
    }

    @EqualsAndHashCode
    @ToString
    public static class HierarchyDetails {
        private String fromField;
        private String toField;
        private String fromSeparator;
        private String toSeparator;
        private PropertySetter onSet;
        private boolean keepEmptySegments;
        private boolean regex;

        public HierarchyDetails() {
        }
        public HierarchyDetails(String fromField, String toField,
                String fromSeparator, String toSeparator) {
            this.fromField = fromField;
            this.toField = toField;
            this.fromSeparator = fromSeparator;
            this.toSeparator = toSeparator;
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
        public String getFromSeparator() {
            return fromSeparator;
        }
        public void setFromSeparator(String fromSeparator) {
            this.fromSeparator = fromSeparator;
        }
        public String getToSeparator() {
            return toSeparator;
        }
        public void setToSeparator(String toSeparator) {
            this.toSeparator = toSeparator;
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
        public boolean isKeepEmptySegments() {
            return keepEmptySegments;
        }
        public void setKeepEmptySegments(boolean keepEmptySegments) {
            this.keepEmptySegments = keepEmptySegments;
        }
        public boolean isRegex() {
            return regex;
        }
        public void setRegex(boolean regex) {
            this.regex = regex;
        }
    }
}
