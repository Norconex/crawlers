/* Copyright 2020-2022 Norconex Inc.
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

import java.io.IOException;
import java.io.InputStream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.AbstractDocumentFilter;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.CharsetUtil;
import com.norconex.importer.util.DOMUtil;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

/**
 * <p>Uses a Document Object Model (DOM) representation of an HTML, XHTML, or
 * XML document content to perform filtering based on matching an
 * element/attribute or element/attribute value.
 * </p>
 * <p>
 * In order to construct a DOM tree, text is loaded entirely
 * into memory. It uses the document content by default, but it can also
 * come from specified metadata fields. If multiple fields values are
 * identified/matched as DOM sources, only one needs to match for the filter
 * to be applied.
 * Use this filter with caution if you know you'll need to parse
 * huge files. You can use {@link TextFilter} instead if this is a
 * concern.
 * </p>
 * <p>
 * The <a href="http://jsoup.org/">jsoup</a> parser library is used to load a
 * document content into a DOM tree. Elements are referenced using a
 * <a href="http://jsoup.org/cookbook/extracting-data/selector-syntax">
 * CSS or JQuery-like syntax</a>.
 * </p>
 * <p>
 * If an element is referenced without a value to match, its mere presence
 * constitutes a match. If both an element and a regular expression is provided
 * the element value will be retrieved and the regular expression will be
 * applied against it for a match.
 * </p>
 * <p>
 * Refer to {@link AbstractDocumentFilter} for the inclusion/exclusion logic.
 * </p>
 * <p>Should be used as a pre-parse handler.</p>
 * <h3>Content-types</h3>
 * <p>
 * By default, this filter is restricted to (applies only to) documents matching
 * the restrictions returned by
 * {@link CommonRestrictions#domContentTypes(String)}.
 * You can specify your own content types if you know they represent a file
 * with HTML or XML-like markup tags.  For documents that are
 * incompatible, consider using {@link RegexContentFilter}
 * instead.
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
 * <p>It is possible to control what gets extracted
 * exactly for matching purposes thanks to the "extract" argument of the
 * new method {@link #setExtract(String)}. Possible values are:</p>
 *
 * {@nx.include com.norconex.importer.util.DOMUtil#extract}
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
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.filter.impl.DOMContentFilter"
 *     {@nx.include com.norconex.importer.handler.filter.AbstractDocumentFilter#attributes}
 *     sourceCharset="(character encoding)"
 *     selector="(selector syntax)"
 *     parser="[html|xml]"
 *     extract="[text|html|outerHtml|ownText|data|tagName|val|className|cssSelector|attr(attributeKey)]">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (optional expression matching fields where the DOM text is located)
 *   </fieldMatcher>
 *   <valueMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (optional expression matching selector extracted value)
 *   </valueMatcher>
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <!-- Exclude an HTML page that has one or more GIF images in it: -->
 * <handler class="DOMContentFilter"
 *          selector="img[src$=.gif]" onMatch="exclude" />
 *
 * <!-- Exclude an HTML page that has a paragraph tag with a class called
 *      "disclaimer" and a value containing "skip me": -->
 * <handler class="DOMContentFilter"
 *          selector="p.disclaimer" onMatch="exclude" >
 *   <valueMatcher method="regex">\bskip me\b</valueMatcher>
 * </handler>
 * }
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
@FieldNameConstants
public class DOMFilter extends AbstractDocumentFilter {

    private final TextMatcher fieldMatcher = new TextMatcher();
    private final TextMatcher valueMatcher = new TextMatcher();
    private String selector;
    private String extract;
    private String sourceCharset = null;
    private String parser = DOMUtil.PARSER_HTML;

    public DOMFilter() {
        setOnMatch(OnMatch.INCLUDE);
        addRestrictions(
                CommonRestrictions.domContentTypes(DocMetadata.CONTENT_TYPE));
    }

    public String getSelector() {
        return selector;
    }
    public void setSelector(String selector) {
        this.selector = selector;
    }

    /**
     * Gets this filter field matcher (copy).
     * @return field matcher
     */
    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }
    /**
     * Sets this filter field matcher (copy).
     * @param fieldMatcher field matcher
     */
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    /**
     * Gets this filter value matcher (copy).
     * @return value matcher
     */
    public TextMatcher getValueMatcher() {
        return valueMatcher;
    }
    /**
     * Sets this filter value matcher (copy).
     * @param valueMatcher value matcher
     */
    public void setValueMatcher(TextMatcher valueMatcher) {
        this.valueMatcher.copyFrom(valueMatcher);
    }
    /**
     * Gets what should be extracted for the value. One of
     * "text" (default), "html", or "outerHtml". <code>null</code> means
     * this class will use the default ("text").
     * @return what should be extracted for the value
     */
    public String getExtract() {
        return extract;
    }
    /**
     * Sets what should be extracted for the value. One of
     * "text" (default), "html", or "outerHtml". <code>null</code> means
     * this class will use the default ("text").
     * @param extract what should be extracted for the value
     */
    public void setExtract(String extract) {
        this.extract = extract;
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
    protected boolean isDocumentMatched(
            HandlerDoc doc, InputStream input, ParseState parseState)
                    throws ImporterHandlerException {

        try {
            if (fieldMatcher.getPattern() != null) {
                // Dealing with field values
                for (String value :
                        doc.getMetadata().matchKeys(fieldMatcher).valueList()) {
                    if (isDocumentMatched(Jsoup.parse(value, doc.getReference(),
                            DOMUtil.toJSoupParser(getParser())))) {
                        return true;
                    }
                }
                return false;
            }
            // Dealing with doc content
            var inputCharset = CharsetUtil.firstNonBlankOrUTF8(
                    parseState,
                    sourceCharset,
                    doc.getDocInfo().getContentEncoding());
            return isDocumentMatched(Jsoup.parse(
                    input, inputCharset, doc.getReference(),
                    DOMUtil.toJSoupParser(getParser())));
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Cannot parse document into a DOM-tree.", e);
        }
    }

    private boolean isDocumentMatched(Document doc) {
        var elms = doc.select(selector);
        // no elements matching
        if (elms.isEmpty()) {
            return false;
        }
        // one or more elements matching
        for (Element elm : elms) {
            var value = DOMUtil.getElementValue(elm, getExtract());
            if (valueMatcher.getPattern() == null
                    || valueMatcher.matches(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void loadFilterFromXML(XML xml) {
        setSelector(xml.getString(attr(Fields.selector), selector));
        setParser(xml.getString(attr(Fields.parser), parser));
        setSourceCharset(
                xml.getString(attr(Fields.sourceCharset), sourceCharset));
        setExtract(xml.getString(attr(Fields.extract), extract));
        fieldMatcher.loadFromXML(xml.getXML(Fields.fieldMatcher));
        valueMatcher.loadFromXML(xml.getXML(Fields.valueMatcher));
    }
    @Override
    protected void saveFilterToXML(XML xml) {
        xml.setAttribute(Fields.selector, selector);
        xml.setAttribute(Fields.parser, parser);
        xml.setAttribute(Fields.sourceCharset, sourceCharset);
        xml.setAttribute(Fields.extract, extract);
        fieldMatcher.saveToXML(xml.addElement(Fields.fieldMatcher));
        valueMatcher.saveToXML(xml.addElement(Fields.valueMatcher));
    }
}
