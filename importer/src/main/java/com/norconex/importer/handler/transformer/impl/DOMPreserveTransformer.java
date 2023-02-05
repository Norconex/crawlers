/* Copyright 2022 Norconex Inc.
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
package com.norconex.importer.handler.transformer.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.impl.DOMTagger;
import com.norconex.importer.handler.transformer.AbstractDocumentTransformer;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.CharsetUtil;
import com.norconex.importer.util.DOMUtil;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Preserves only one or more elements matching a given selector from
 * a document content. Applies to HTML, XHTML, or XML document.
 * To store preserved values into fields, use {@link DOMTagger}
 * instead.
 * </p>
 * <p>
 * This class constructs a DOM tree from a document or field content.
 * That DOM tree is loaded entirely into memory. Use this transformer with
 * caution if you know you'll need to parse huge files.
 * </p>
 * <p>
 * The <a href="http://jsoup.org/">jsoup</a> parser library is used to load a
 * document content into a DOM tree. Elements are referenced using a
 * <a href="http://jsoup.org/cookbook/extracting-data/selector-syntax">
 * CSS or JQuery-like syntax</a>.
 * </p>
 * <p>Should be used as a pre-parse handler.</p>
 *
 * <h3>Content-types</h3>
 * <p>
 * By default, this filter is restricted to (applies only to) documents matching
 * the restrictions returned by
 * {@link CommonRestrictions#domContentTypes(String)}.
 * You can specify your own content types if you know they represent a file
 * with HTML or XML-like markup tags.
 * </p>
 * <p>
 * When used as a pre-parse handler,
 * this class attempts to detect the content character
 * encoding unless the character encoding
 * was specified using {@link #setSourceCharset(String)}. Since document
 * parsing converts content to UTF-8, UTF-8 is always assumed when
 * used as a post-parse handler.
 * </p>
 * <p>
 * You can control what gets preserved
 * exactly thanks to the "extract" argument of
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
 * <p>
 * You can specify a <code>defaultValue</code>
 * on each DOM extraction details. When no match occurred for a given selector,
 * the default value will be inserted in the modified document content.
 * When matching blanks (see below) you will get
 * an empty string as opposed to the default value.
 * Empty strings and spaces are supported as default values
 * (the default value is now taken literally).
 * </p>
 * <p>
 * You can set <code>matchBlanks</code> to
 * <code>true</code> to match elements that are present
 * but have blank values. Blank values are empty values or values containing
 * white spaces only. Because white spaces are normalized by the DOM parser,
 * such matches will always return an empty string (spaces will be trimmed).
 * By default elements with blank values are not matched and are ignored.
 * </p>
 * <p>
 * You can specify which parser to use when reading
 * documents. The default is "html" and will normalize the content
 * as HTML. This is generally a desired behavior, but this can sometimes
 * have your selector fail. If you encounter this
 * problem, try switching to "xml" parser, which does not attempt normalization
 * on the content. The drawback with "xml" is you may not get all HTML-specific
 * selector options to work.  If you know you are dealing with XML to begin
 * with, specifying "xml" should be a good option.
 * </p>
 *
 * <h3>Multiple preserved elements</h3>
 * <p>
 * It is possible to preserve multiple elements or text.  Specifying multiple
 * DOM selector will achieve that.  Each potential match is always
 * performed on the DOM as it was received.
 * You can use with {@link DOMDeleteTransformer} for additional flexibility.
 * </p>
 * <p>
 * It is important to note that preserved elements and text may not always form
 * valid XML when put back together.  If your goal is to have the Importer
 * parser extracts the raw text from it like any other documents, this is not an
 * issue, but it could be if you want to use the new document content as XML
 * in a different context.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.transformer.impl.DOMPreserveTransformer"
 *         parser="[html|xml]"
 *         sourceCharset="(character encoding)">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple "dom" tags allowed -->
 *   <dom selector="(selector syntax)"
 *       extract="[text|html|outerHtml|ownText|data|tagName|val|className|cssSelector|attr(attributeKey)]"
 *       matchBlanks="[false|true]"
 *       defaultValue="(optional value to use when no match)"/>
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="DOMPreserveTransformer">
 *   <dom selector="div.firstName" extract="outerHtml" />
 *   <dom selector="div.lastName" extract="outerHtml" />
 * </handler>
 * }
 * <p>
 * Given this HTML snippet...
 * </p>
 * <pre>
 * &lt;div&gt;
 *   &lt;div class="firstName"&gt;Joe&lt;/div&gt;
 *   &lt;div class="lastName"&gt;Dalton&lt;/div&gt;
 *   &lt;div class="city"&gt;Daisy Town&lt;/div&gt;
 * &lt;/div&gt;
 * </pre>
 * <p>
 * ... the above example will result in the document content having
 * the following:
 * </p>
 * <pre>
 *   &lt;div class="firstName"&gt;Joe&lt;/div&gt;
 *   &lt;div class="lastName"&gt;Dalton&lt;/div&gt;
 * </pre>
 *
 * @see DOMTagger
 * @see DOMDeleteTransformer
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class DOMPreserveTransformer extends AbstractDocumentTransformer {

    private final List<DOMExtractDetails> extractions = new ArrayList<>();
    private String sourceCharset = null;
    private String parser = DOMUtil.PARSER_HTML;

    /**
     * Constructor.
     */
    public DOMPreserveTransformer() {
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
    protected void transformApplicableDocument(HandlerDoc doc,
            InputStream document, OutputStream output, ParseState parseState)
                    throws ImporterHandlerException {
        var ref = doc.getReference();
        try {
            var inputCharset = CharsetUtil.firstNonBlankOrUTF8(
                    parseState,
                    sourceCharset,
                    doc.getDocInfo().getContentEncoding());
            IOUtils.write(handle(Jsoup.parse(document, inputCharset, ref,
                    DOMUtil.toJSoupParser(getParser()))),
                    output, inputCharset);
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Cannot process DOM element(s) from DOM-tree.", e);
        }
    }

    private String handle(Document jsoupDoc) {
        List<String> extractedValues = new ArrayList<>();
        for (DOMExtractDetails details : extractions) {
            domExtractDoc(extractedValues, jsoupDoc, details);
        }
        return StringUtils.join(extractedValues, '\n');
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
            var value = DOMUtil.getElementValue(elm, details.extract);
            // JSoup normalizes white spaces and should always trim them,
            // but we force it here to ensure 100% consistency.
            value = StringUtils.trim(value);
            var matches = ((value != null)
                    && (details.matchBlanks || !StringUtils.isBlank(value)));
            if (matches) {
                extractedValues.add(value);
            } else if (hasDefault) {
                extractedValues.add(details.getDefaultValue());
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

    /**
     * Removes all DOM extraction details.
     */
    public void removeDOMExtractDetailsList() {
        synchronized (extractions) {
            extractions.clear();
        }
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setSourceCharset(xml.getString("@sourceCharset", sourceCharset));
        setParser(xml.getString("@parser", parser));
        var nodes = xml.getXMLList("dom");
        if (!nodes.isEmpty()) {
            extractions.clear();
        }
        for (XML node : nodes) {
            node.checkDeprecated("@overwrite", "onSet", true);
            var details = new DOMExtractDetails(
                    node.getString("@selector", null),
                    node.getString("@extract", null));
            details.setMatchBlanks(node.getBoolean("@matchBlanks", false));
            details.setDefaultValue(node.getString("@defaultValue", null));
            addDOMExtractDetails(details);
        }
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("sourceCharset", sourceCharset);
        xml.setAttribute("parser", parser);
        for (DOMExtractDetails details : extractions) {
            xml.addElement("dom")
                .setAttribute("selector", details.getSelector())
                .setAttribute("extract", details.getExtract())
                .setAttribute("matchBlanks", details.isMatchBlanks())
                .setAttribute("defaultValue", details.getDefaultValue());
        }
    }

    /**
     * DOM Extraction Details
     */
    @EqualsAndHashCode
    @ToString
    public static class DOMExtractDetails {
        private String selector;
        private String extract;
        private boolean matchBlanks;
        private String defaultValue;

        public DOMExtractDetails() {
        }
        public DOMExtractDetails(
                String selector) {
            this(selector, null);
        }
        public DOMExtractDetails(String selector, String extract) {
            this.selector = selector;
            this.extract = extract;
        }

        public String getSelector() {
            return selector;
        }
        public DOMExtractDetails setSelector(String selector) {
            this.selector = selector;
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

        public String getDefaultValue() {
            return defaultValue;
        }
        public DOMExtractDetails setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }
    }
}
