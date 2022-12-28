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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.handler.transformer.impl.DOMDeleteTransformer;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.CharsetUtil;
import com.norconex.importer.util.DOMUtil;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Extract the value of one or more elements or attributes into
 * a target field, or delete matching elements. Applies to
 * HTML, XHTML, or XML document.</p>
 * <p>
 * This class constructs a DOM tree from a document or field content.
 * That DOM tree is loaded entirely into memory. Use this tagger with caution
 * if you know you'll need to parse huge files. It may be preferable to use
 * {@link RegexTagger} if this is a concern. Also, to help performance
 * and avoid re-creating DOM tree before every DOM extraction you want to
 * perform, try to combine multiple extractions in a single instance
 * of this Tagger.
 * </p>
 * <p>
 * The <a href="http://jsoup.org/">jsoup</a> parser library is used to load a
 * document content into a DOM tree. Elements are referenced using a
 * <a href="http://jsoup.org/cookbook/extracting-data/selector-syntax">
 * CSS or JQuery-like syntax</a>.
 * </p>
 * <p>Should be used as a pre-parse handler.</p>
 *
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 *
 * <h3>Content-types</h3>
 * <p>
 * By default, this filter is restricted to (applies only to) documents matching
 * the restrictions returned by
 * {@link CommonRestrictions#domContentTypes(String)}.
 * You can specify your own content types if you know they represent a file
 * with HTML or XML-like markup tags.
 * </p>
 *
 * <p>When used as a pre-parse handler,
 * this class attempts to detect the content character
 * encoding unless the character encoding
 * was specified using {@link #setSourceCharset(String)}. Since document
 * parsing converts content to UTF-8, UTF-8 is always assumed when
 * used as a post-parse handler.
 * </p>
 *
 * <p>You can control what gets extracted
 * exactly thanks to the "extract" argument of the new method
 * {@link DOMExtractDetails#setExtract(String)}. Possible values are:</p>
 * <ul>
 *   <li><b>text</b>: Default option when extract is blank. The text of
 *       the element, including combined children.</li>
 *   <li><b>html</b>: Extracts an element inner
 *       HTML (including children).</li>
 *   <li><b>outerHtml</b>: Extracts an element outer
 *       HTML (like "html", but includes the "current" tag).</li>
 *   <li><b>ownText</b>: Extracts the text owned by this element only;
 *       does not get the combined text of all children.</li>
 *   <li><b>data</b>: Extracts the combined data of a data-element (e.g.
 *       &lt;script&gt;).</li>
 *   <li><b>id</b>: Extracts the ID attribute of the element (if any).</li>
 *   <li><b>tagName</b>: Extract the name of the tag of the element.</li>
 *   <li><b>val</b>: Extracts the value of a form element
 *       (input, textarea, etc).</li>
 *   <li><b>className</b>: Extracts the literal value of the element's
 *       "class" attribute, which may include multiple class names,
 *       space separated.</li>
 *   <li><b>cssSelector</b>: Extracts a CSS selector that will uniquely
 *       select (identify) this element.</li>
 *   <li><b>attr(attributeKey)</b>: Extracts the value of the element
 *       attribute matching your replacement for "attributeKey"
 *       (e.g. "attr(title)" will extract the "title" attribute).</li>
 * </ul>
 *
 * <p>You can specify a <code>fromField</code>
 * as the source of the HTML to parse instead of using the document content.
 * If multiple values are present for that source field, DOM extraction will be
 * applied to each value.
 * </p>
 *
 * <p>You can specify a <code>defaultValue</code>
 * on each DOM extraction details. When no match occurred for a given selector,
 * the default value will be stored in the <code>toField</code> (as opposed
 * to not storing anything).  When matching blanks (see below) you will get
 * an empty string as opposed to the default value.
 * Empty strings and spaces are supported as default values
 * (the default value is now taken literally).
 * </p>
 *
 * <p>You can set <code>matchBlanks</code> to
 * <code>true</code> to match elements that are present
 * but have blank values. Blank values are empty values or values containing
 * white spaces only. Because white spaces are normalized by the DOM parser,
 * such matches will always return an empty string (spaces will be trimmed).
 * By default elements with blank values are not matched and are ignored.
 * </p>
 *
 * <p>You can specify which parser to use when reading
 * documents. The default is "html" and will normalize the content
 * as HTML. This is generally a desired behavior, but this can sometimes
 * have your selector fail. If you encounter this
 * problem, try switching to "xml" parser, which does not attempt normalization
 * on the content. The drawback with "xml" is you may not get all HTML-specific
 * selector options to work.  If you know you are dealing with XML to begin
 * with, specifying "xml" should be a good option.
 * </p>
 *
 * <h3>Content deletion from fields</h3>
 * <p>
 * As of 3.0.0, you can specify whether to delete any elements
 * matched by the selector. You can use with a "toField" or on its own.
 * Some options are ignored by deletions, such as
 * "extract" or "defaultValue".  Because taggers cannot modify the document
 * content, deletion only applies to metadata fields. Use {@link DOMDeleteTransformer}
 * to modify the document content.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.DOMTagger"
 *         fromField="(optional source field)"
 *         parser="[html|xml]"
 *         sourceCharset="(character encoding)">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple "dom" tags allowed -->
 *   <dom selector="(selector syntax)"
 *       toField="(target field)"
 *       extract="[text|html|outerHtml|ownText|data|tagName|val|className|cssSelector|attr(attributeKey)]"
 *       matchBlanks="[false|true]"
 *       defaultValue="(optional value to use when no match)"
 *       delete="[false|true]"
 *       {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}/>
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="DOMTagger">
 *   <dom selector="div.firstName" toField="firstName" />
 *   <dom selector="div.lastName"  toField="lastName" />
 * </handler>
 * }
 * <p>
 * Given this HTML snippet...
 * </p>
 * <pre>
 * &lt;div class="firstName"&gt;Joe&lt;/div&gt;
 * &lt;div class="lastName"&gt;Dalton&lt;/div&gt;
 * </pre>
 * <p>
 * ... the above example will store "Joe" in a "firstName" field and "Dalton"
 * in a "lastName" field.
 * </p>
 * @see DOMDeleteTransformer
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class DOMTagger extends AbstractDocumentTagger {

    private static final Logger LOG = LoggerFactory.getLogger(DOMTagger.class);

    private final List<DOMExtractDetails> extractions = new ArrayList<>();
    private String sourceCharset = null;
    private String fromField = null;
    private String parser = DOMUtil.PARSER_HTML;

    /**
     * Constructor.
     */
    public DOMTagger() {
        addRestrictions(
                CommonRestrictions.domContentTypes(DocMetadata.CONTENT_TYPE));
    }

    /**
     * Gets the assumed source character encoding.
     * @return character encoding of the source to be transformed
     */
    public String getSourceCharset() {
        return sourceCharset;
    }
    /**
     * Sets the assumed source character encoding.
     * @param sourceCharset character encoding of the source to be transformed
     */
    public void setSourceCharset(String sourceCharset) {
        this.sourceCharset = sourceCharset;
    }

    /**
     * Gets optional source field holding the HTML content to apply DOM
     * extraction to.
     * @return from field
     */
    public String getFromField() {
        return fromField;
    }
    /**
     * Sets optional source field holding the HTML content to apply DOM
     * extraction to.
     * @param fromField from field
     */
    public void setFromField(String fromField) {
        this.fromField = fromField;
    }

    /**
     * Gets the parser to use when creating the DOM-tree.
     * @return <code>html</code> (default) or <code>xml</code>.
     */
    public String getParser() {
        return parser;
    }
    /**
     * Sets the parser to use when creating the DOM-tree.
     * @param parser <code>html</code> or <code>xml</code>.
     */
    public void setParser(String parser) {
        this.parser = parser;
    }

    @Override
    public void tagApplicableDocument(
            HandlerDoc doc, InputStream document, ParseState parseState)
                    throws ImporterHandlerException {
        var ref = doc.getReference();
        var meta = doc.getMetadata();
        try {
            // Use "fromField" as content
            if (StringUtils.isNotBlank(getFromField())) {
                var fromValues = meta.getStrings(getFromField());
                if (fromValues.isEmpty()) {
                    LOG.debug("Field \"{}\" has no value. No DOM "
                            + "extraction performed.", getFromField());
                    return;
                }
                for (var i = 0; i < fromValues.size(); i++) {
                    var fromValue = fromValues.get(i);
                    if (StringUtils.isNotBlank(fromValue)) {
                        fromValues.set(i, handle(Jsoup.parse(fromValue, ref,
                                DOMUtil.toJSoupParser(getParser())), meta));
                    }
                }
                meta.setList(getFromField(), fromValues);
            // Use doc content
            } else {
                var inputCharset = CharsetUtil.firstNonBlankOrUTF8(
                        parseState,
                        sourceCharset,
                        doc.getDocInfo().getContentEncoding());
                handle(Jsoup.parse(document, inputCharset, ref,
                        DOMUtil.toJSoupParser(getParser())), meta);
            }
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Cannot process DOM element(s) from DOM-tree.", e);
        }
    }


    private String handle(Document jsoupDoc, Properties metadata) {
        for (DOMExtractDetails details : extractions) {
            List<String> extractedValues = new ArrayList<>();
            domExtractDoc(extractedValues, jsoupDoc, details);
            if (!extractedValues.isEmpty()) {
                PropertySetter.orAppend(details.getOnSet()).apply(
                        metadata, details.toField, extractedValues);
            }
        }

        return jsoupDoc.toString();
    }

    private void domExtractDoc(List<String> extractedValues,
            Document doc, DOMExtractDetails details) {
        var elms = doc.select(StringUtils.trim(details.selector));
        var hasDefault = details.getDefaultValue() != null;

        // no elements matching
        if (elms.isEmpty()) {
            if (hasDefault) {
                extractedValues.add(details.getDefaultValue());
            }
            return;
        }

        // one or more elements matching
        for (Element elm : elms) {
            if (StringUtils.isNotBlank(details.toField)) {
                var value = DOMUtil.getElementValue(elm, details.extract);
                // JSoup normalizes white spaces and should always trim them,
                // but we force it here to ensure 100% consistency.
                value = StringUtils.trim(value);
                var matches = ((value != null) && (details.matchBlanks || !StringUtils.isBlank(value)));
                if (matches) {
                    extractedValues.add(value);
                } else if (hasDefault) {
                    extractedValues.add(details.getDefaultValue());
                }
            }
            if (details.delete) {
                elm.remove();
            }
        }
    }

    /**
     * Adds DOM extraction details.
     * @param extractDetails DOM extraction details
     */
    public void addDOMExtractDetails(DOMExtractDetails extractDetails) {
        if (extractDetails != null) {
            extractions.add(extractDetails);
        }
    }

    /**
     * Gets a list of DOM extraction details.
     * @return list of DOM extraction details.
     */
    public List<DOMExtractDetails> getDOMExtractDetailsList() {
        return Collections.unmodifiableList(extractions);
    }

    /**
     * Removes the DOM extraction details matching the given selector
     * @param selector DOM selector
     */
    public void removeDOMExtractDetails(String selector) {
        List<DOMExtractDetails> toRemove = new ArrayList<>();
        for (DOMExtractDetails details : extractions) {
            if (Objects.equals(details.getSelector(), selector)) {
                toRemove.add(details);
            }
        }
        synchronized (extractions) {
            extractions.removeAll(toRemove);
        }
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setSourceCharset(xml.getString("@sourceCharset", sourceCharset));
        setFromField(xml.getString("@fromField", fromField));
        setParser(xml.getString("@parser", parser));
        var nodes = xml.getXMLList("dom");
        if (!nodes.isEmpty()) {
            extractions.clear();
        }
        for (XML node : nodes) {
            node.checkDeprecated("@overwrite", "onSet", true);
            var details = new DOMExtractDetails(
                    node.getString("@selector", null),
                    node.getString("@toField", null),
                    PropertySetter.fromXML(node, null),
                    node.getString("@extract", null));
            details.setMatchBlanks(node.getBoolean("@matchBlanks", false));
            details.setDelete(node.getBoolean("@delete", false));
            details.setDefaultValue(node.getString("@defaultValue", null));
            addDOMExtractDetails(details);
        }
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("sourceCharset", sourceCharset);
        xml.setAttribute("fromField", fromField);
        xml.setAttribute("parser", parser);
        for (DOMExtractDetails details : extractions) {
            var node = xml.addElement("dom")
                    .setAttribute("selector", details.getSelector())
                    .setAttribute("toField", details.getToField())
                    .setAttribute("extract", details.getExtract())
                    .setAttribute("matchBlanks", details.isMatchBlanks())
                    .setAttribute("delete", details.isDelete())
                    .setAttribute("defaultValue", details.getDefaultValue());
            PropertySetter.toXML(node, details.getOnSet());
        }
    }

    /**
     * DOM Extraction Details
     */
    @EqualsAndHashCode
    @ToString
    public static class DOMExtractDetails {
        private String selector;
        private String toField;
        private PropertySetter onSet;
        private String extract;
        private boolean matchBlanks;
        private boolean delete;
        private String defaultValue;

        public DOMExtractDetails() {
        }
        public DOMExtractDetails(
                String selector, String to, PropertySetter onSet) {
            this(selector, to, onSet, null);
        }
        public DOMExtractDetails(String selector, String to,
                PropertySetter onSet, String extract) {
            this.selector = selector;
            toField = to;
            this.onSet = onSet;
            this.extract = extract;
        }

        public String getSelector() {
            return selector;
        }
        public DOMExtractDetails setSelector(String selector) {
            this.selector = selector;
            return this;
        }

        public String getToField() {
            return toField;
        }
        public DOMExtractDetails setToField(String toField) {
            this.toField = toField;
            return this;
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
         * @return DOM extraction details
         */
        public DOMExtractDetails setOnSet(PropertySetter onSet) {
            this.onSet = onSet;
            return this;
        }

        public String getExtract() {
            return extract;
        }
        public DOMExtractDetails setExtract(String extract) {
            this.extract = extract;
            return this;
        }

        /**
         * Gets whether elements with blank values should be considered a
         * match and have an empty string returned as opposed to nothing at all.
         * Default is <code>false</code>;
         * @return <code>true</code> if elements with blank values are supported
         */
        public boolean isMatchBlanks() {
            return matchBlanks;
        }
        /**
         * Sets whether elements with blank values should be considered a
         * match and have an empty string returned as opposed to nothing at all.
         * @param matchBlanks <code>true</code> to support elements with
         *                    blank values
         * @return DOM extraction details
         */
        public DOMExtractDetails setMatchBlanks(boolean matchBlanks) {
            this.matchBlanks = matchBlanks;
            return this;
        }

        /**
         * Gets whether to delete DOM attributes/elements matching the
         * specified selector.
         * @return <code>true</code> if deleting
         */
        public boolean isDelete() {
            return delete;
        }
        /**
         * Sets whether to delete DOM attributes/elements matching the
         * specified selector.
         * @param delete <code>true</code> if deleting
         * @return DOM extraction details
         */
        public DOMExtractDetails setDelete(boolean delete) {
            this.delete = delete;
            return this;
        }

        public String getDefaultValue() {
            return defaultValue;
        }
        public DOMExtractDetails setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }
    }
}
