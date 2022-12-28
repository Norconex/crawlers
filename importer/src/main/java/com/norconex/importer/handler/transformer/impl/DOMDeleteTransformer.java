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
package com.norconex.importer.handler.transformer.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.norconex.commons.lang.collection.CollectionUtil;
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
 * <p>Enables deletion of one or more elements matching a given selector
 * from a document content. Applies to HTML, XHTML, or XML document.
 * To extract DOM elements into metadata fields, use {@link DOMTagger}
 * instead.
 * </p>
 *
 * <p>
 * This class constructs a DOM tree from the document content. That DOM tree
 * is loaded entirely into memory. Use this transformer with caution if you know
 * you'll need to parse huge files. It may be preferable to use
 * {@link ReplaceTransformer} if this is a concern. Also, to help performance
 * and avoid re-creating DOM tree before every DOM operations you want to
 * perform, try to combine multiple extractions in a single instance
 * of this transformer.
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
 *
 * <p>When used as a pre-parse handler,
 * this class attempts to detect the content character
 * encoding unless the character encoding
 * was specified using {@link #setSourceCharset(String)}.
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
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.transformer.impl.DOMDeleteTransformer"
 *         parser="[html|xml]"
 *         sourceCharset="(character encoding)">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple "dom" tags allowed -->
 *   <dom selector="(selector syntax)" />
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="DOMDeleteTransformer">
 *   <dom selector="div.firstName" />
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
 * ... the above example will delete "Joe" but keep "Dalton".
 * </p>
 * @see DOMTagger
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class DOMDeleteTransformer extends AbstractDocumentTransformer {

    private List<String> selectors = new ArrayList<>();
    private String sourceCharset = null;
    private String parser = DOMUtil.PARSER_HTML;

    /**
     * Constructor.
     */
    public DOMDeleteTransformer() {
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
        try {
            var inputCharset = CharsetUtil.firstNonBlankOrUTF8(
                    parseState,
                    sourceCharset,
                    doc.getDocInfo().getContentEncoding());
            IOUtils.write(handle(Jsoup.parse(document, inputCharset,
                    doc.getReference(), DOMUtil.toJSoupParser(getParser()))),
                    output, inputCharset);
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Cannot process DOM element(s) from DOM-tree.", e);
        }
    }

    private String handle(Document jsoupDoc) {
        for (String selector : selectors) {
            var elms = jsoupDoc.select(StringUtils.trim(selector));
            if (!elms.isEmpty()) {
                for (Element elm : elms) {
                    elm.remove();
                }
            }
        }
        return jsoupDoc.toString();
    }

    public List<String> getSelectors() {
        return Collections.unmodifiableList(selectors);
    }
    public void setSelectors(List<String> selectors) {
        CollectionUtil.setAll(this.selectors, selectors);
    }
    public void addSelector(String selector) {
        if (StringUtils.isNotBlank(selector)) {
            selectors.add(selector);
        }
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setSourceCharset(xml.getString("@sourceCharset", sourceCharset));
        setParser(xml.getString("@parser", parser));
        var nodes = xml.getXMLList("dom");
        if (!nodes.isEmpty()) {
            selectors.clear();
        }
        for (XML node : nodes) {
            node.checkDeprecated("@overwrite", "onSet", true);
            addSelector(node.getString("@selector", null));
        }
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("sourceCharset", sourceCharset);
        xml.setAttribute("parser", parser);
        for (String selector : selectors) {
            xml.addElement("dom").setAttribute("selector", selector);
        }
    }
}
