/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.crawler.web.link.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trim;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.google.common.base.Objects;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.web.link.AbstractTextLinkExtractor;
import com.norconex.crawler.web.link.Link;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.util.DOMUtil;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * Extracts links from a Document Object Model (DOM) representation of an
 * HTML, XHTML, or XML document content based on values of matching
 * elements and attributes.
 * </p>
 * <p>
 * In order to construct a DOM tree, text is loaded entirely
 * into memory. It uses the document content by default, but it can also
 * come from specified metadata fields.
 * Use this filter with caution if you know you'll need to parse
 * huge files. Use the {@link HtmlLinkExtractor} instead if this is a
 * concern.
 * </p>
 * <p>
 * The <a href="http://jsoup.org/">jsoup</a> parser library is used to load a
 * document content into a DOM tree. Elements are referenced using a
 * <a href="http://jsoup.org/cookbook/extracting-data/selector-syntax">
 * CSS or JQuery-like syntax</a>.
 * </p>
 * <p>
 * This link extractor is normally used before importing.
 * </p>
 *
 * <p>When used before importing this class attempts to detect the content
 * character encoding unless the character encoding
 * was specified using {@link #setCharset(String)}. Since document
 * parsing converts content to UTF-8, UTF-8 is always assumed when
 * used as a post-parse handler.
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
 * <h3>Matching links</h3>
 * <p>
 * You can define as many JSoup "selectors" as desired. All values matched
 * by a selector will be extracted as a URL.
 * </p>
 * <p>
 * It is possible to control what gets extracted
 * exactly for matching purposes thanks to the "extract" argument expected
 * with every selector.  Possible values are:
 * </p>
 *
 * {@nx.include com.norconex.importer.util.DOMUtil#extract}
 *
 * <p>
 * When not specified, the default is "text".
 * </p>
 *
 * <p>The default selectors / extract strategies are:</p>
 * <ul>
 *   <li>a[href] / attr(href)</li>
 *   <li>[src] / attr(src)</li>
 *   <li>link[href] / attr(href)</li>
 *   <li>meta[http-equiv='refresh'] / attr(content)</li>
 * </ul>
 * <p>
 * For any extracted link values, this extractor will perform minimal
 * heuristics to clean extra content not part of a regular URL.  For instance,
 * it will only keep what is after <code>url=</code> when dealing with
 * <code>&lt;meta http-equiv</code> refresh URLs.  It will also trim white
 * spaces.
 * </p>
 *
 * <h3>Ignoring link data</h3>
 * <p>
 * By default, contextual information is kept about the HTML/XML mark-up
 * tag from which a link is extracted (e.g., tag name and attributes).
 * That information gets stored as metadata in the target document.
 * If you want to limit the quantity of information extracted/stored,
 * you can disable this feature by setting
 * {@link #ignoreLinkData} to <code>true</code>.
 * </p>
 *
 * <h3>URL Schemes</h3>
 * <p>Only valid
 * <a href="https://en.wikipedia.org/wiki/Uniform_Resource_Identifier#Syntax">
 * schemes</a> are extracted for absolute URLs. By default, those are
 * <code>http</code>, <code>https</code>, and <code>ftp</code>. You can
 * specify your own list of supported protocols with
 * {@link #setSchemes(String[])}.
 * </p>
 *
 * <h3>Applicable documents</h3>
 * <p>
 * By default, this extractor only will be applied on documents matching
 * one of these content types:
 * </p>
 * {@nx.include com.norconex.importer.handler.CommonMatchers#domContentTypes}
 *
 * <h3>"nofollow"</h3>
 * <p>
 * By default, a regular HTML link having the "rel" attribute set to "nofollow"
 * won't be extracted (e.g.
 * <code>&lt;a href="x.html" rel="nofollow" ...&gt;</code>).
 * To force its extraction (and ensure it is followed) you can set
 * {@link #setIgnoreNofollow(boolean)} to <code>true</code>.
 * </p>
 *
 * {@nx.xml.usage
 * <extractor class="com.norconex.crawler.web.link.impl.DOMLinkExtractor"
 *     ignoreNofollow="[false|true]"
 *     ignoreLinkData="[false|true]"
 *     parser="[html|xml]"
 *     charset="(supported character encoding)">
 *   {@nx.include com.norconex.crawler.web.link.AbstractTextLinkExtractor@nx.xml.usage}
 *
 *   <schemes>
 *     (CSV list of URI scheme for which to perform link extraction.
 *      leave blank or remove tag to use defaults.)
 *   </schemes>
 *
 *   <!-- Repeat as needed: -->
 *   <linkSelector
 *       {@nx.include com.norconex.importer.util.DOMUtil#attributes}>
 *      (selector syntax)
 *   </linkSelector>
 *
 *   <!-- Optional. Only apply link selectors to portions of a document
 *        matching these selectors. Repeat as needed. -->
 *   <extractSelector>(selector syntax)</extractSelector>
 *
 *   <!-- Optional. Do not apply link selectors to portions of a document
 *        matching these selectors. Repeat as needed. -->
 *   <noExtractSelector>(selector syntax)</noExtractSelector>
 *
 * </extractor>
 * }
 *
 * {@nx.xml.example
 * <extractor class="com.norconex.crawler.web.link.impl.DOMLinkExtractor">
 *   <linkSelector extract="attr(href)">a[href]</linkSelector>
 *   <linkSelector extract="attr(src)">[src]</linkSelector>
 *   <linkSelector extract="attr(href)">link[href]</linkSelector>
 *   <linkSelector extract="attr(content)">meta[http-equiv='refresh']</linkSelector>
 *
 *   <linkSelector extract="attr(data-myurl)">[data-myurl]</linkSelector>
 * </extractor>
 * }
 *
 * <p>
 * The above example will extract URLs found in custom element attributes named
 * <code>data-myurl</code>.
 * </p>
 * @since 3.0.0
 */
@SuppressWarnings("javadoc")
@Data
public class DOMLinkExtractor extends AbstractTextLinkExtractor {

    private static final List<String> DEFAULT_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("http", "https", "ftp"));

    @Setter(value = AccessLevel.NONE)
    @Getter(value = AccessLevel.NONE)
    private final Map<String, String> linkSelectors = new ListOrderedMap<>();
    private final List<String> extractSelectors = new ArrayList<>();
    private final List<String> noExtractSelectors = new ArrayList<>();

    /**
     * The assumed source character encoding.
     * @param charset character encoding of the source content
     * @return character encoding of the source content
     */
    private String charset = null;
    /**
     * The parser to use when creating the DOM-tree.
     * @param parser <code>html</code> or <code>xml</code>.
     * @return <code>html</code> (default) or <code>xml</code>.
     */
    private String parser = DOMUtil.PARSER_HTML;
    private boolean ignoreNofollow;
    /**
     * Whether to ignore extra data associated with a link.
     * @param ignoreLinkData <code>true</code> to ignore.
     * @return <code>true</code> to ignore.
     */
    private boolean ignoreLinkData;
    private final List<String> schemes = new ArrayList<>(DEFAULT_SCHEMES);

    public DOMLinkExtractor() {
        setRestrictions(CommonRestrictions.domContentTypes(
                DocMetadata.CONTENT_TYPE));
        addLinkSelector("a[href]", "attr(href)");
        addLinkSelector("[src]", "attr(src)");
        addLinkSelector("link[href]", "attr(href)");
        addLinkSelector("meta[http-equiv='refresh']", "attr(content)");
    }

    /**
     * Adds a new link selector extracting the "text" from matches.
     * @param selector JSoup selector
     */
    public void addLinkSelector(String selector) {
        addLinkSelector(selector, null);
    }
    public void addLinkSelector(String selector, String extract) {
        linkSelectors.put(
                trim(selector), isBlank(extract) ? "text" : trim(extract));
    }
    public void removeLinkSelector(String selector) {
        linkSelectors.remove(trim(selector));
    }
    public void clearLinkSelectors() {
        linkSelectors.clear();
    }

    public List<String> getExtractSelectors() {
        return Collections.unmodifiableList(extractSelectors);
    }
    public void setExtractSelectors(List<String> selectors) {
        CollectionUtil.setAll(extractSelectors, selectors);
    }
    public void addExtractSelectors(List<String> selectors) {
        extractSelectors.addAll(selectors);
    }

    public List<String> getNoExtractSelectors() {
        return Collections.unmodifiableList(noExtractSelectors);
    }
    public void setNoExtractSelectors(List<String> selectors) {
        CollectionUtil.setAll(noExtractSelectors, selectors);
    }
    public void addNoExtractSelectors(List<String> selectors) {
        noExtractSelectors.addAll(selectors);
    }

    /**
     * Gets the schemes to be extracted.
     * @return schemes to be extracted
     */
    public List<String> getSchemes() {
        return Collections.unmodifiableList(schemes);
    }
    /**
     * Sets the schemes to be extracted.
     * @param schemes schemes to be extracted
     */
    public void setSchemes(List<String> schemes) {
        CollectionUtil.setAll(this.schemes, schemes);
    }

    @Override
    public void extractTextLinks(
            Set<Link> links, HandlerDoc doc, Reader reader) throws IOException {
        var jparser = DOMUtil.toJSoupParser(parser);
        var jdoc = jparser.parseInput(reader, doc.getReference());
        jdoc = excludeUnwantedContent(jdoc);

        for (Entry<String, String> sel : linkSelectors.entrySet()) {
            for (Element elm : jdoc.select(sel.getKey())) {
                var link = extractLink(elm, sel.getValue());
                if (link != null) {
                    links.add(link);
                }
            }
        }
    }

    private Link extractLink(Element elm, String extract) {
        var url = trimToNull(DOMUtil.getElementValue(elm, extract));
        if (url == null
                || (!isIgnoreNofollow() && elm.is("[rel='nofollow']"))) {
            return null;
        }

        // Do a bit of clean-up
        if (elm.is("meta[http-equiv='refresh']") && elm.hasAttr("content")) {
            url = url.replaceAll("\\s+", " ");
            url = url.replaceFirst(
                    "(?i)^.*\\burl\\s?=\\s?([\"']?)\\s?([^\\s]+)\\s?\\1", "$2");
            url = StringUtils.strip(url, "\"'");
        }

        // Make absolute (can't rely on JSoup "abs:" since URL can be defined
        // in XML elements as well, not just attributes).
        url = HttpURL.toAbsolute(elm.baseUri(), url);


        // Make sure URL scheme is supported
        var supportedSchemes =
                schemes.isEmpty() ? DEFAULT_SCHEMES : schemes;
        if (!supportedSchemes.contains(StringUtils.substringBefore(url, ":"))) {
            return null;
        }

        // Build and return link
        var link = new Link(url);
        link.setReferrer(elm.baseUri());
        var linkMeta = link.getMetadata();

        // Link data
        if (!ignoreLinkData) {
            var m = Pattern.compile(
                    "(?i)^attr\\((.*?)\\)$").matcher(extract);
            String urlAttr = null;
            if (m.matches()) {
                urlAttr = m.group(1);
                linkMeta.set("attr", urlAttr);
            }
            linkMeta.set("tag", elm.tagName());
            if (StringUtils.isNotBlank(elm.text())) {
                linkMeta.set("text", elm.text());
            }

            for (Attribute attr : elm.attributes()) {
                var key = attr.getKey();
                var value = attr.getValue();
                if (!Objects.equal(urlAttr, attr.getKey())
                        && StringUtils.isNotBlank(value)) {
                    linkMeta.set("attr." + key, value);
                }
            }
        }
        return link;
    }

    private Document excludeUnwantedContent(Document doc) {
        var newDoc = doc;

        // we join multiple selectors into one so matches are read in order.
        var extractSelector = StringUtils.join(extractSelectors, ", ");
        if (StringUtils.isNotBlank(extractSelector)) {
            var copyDoc = new Document(newDoc.baseUri());
            for (Element el : newDoc.select(extractSelector)) {
                copyDoc.appendChild(el);
            }
            newDoc = copyDoc;
        }

        for (String selector : noExtractSelectors) {
            newDoc.select(selector).forEach(Element::remove);
        }
        return newDoc;
    }

    @Override
    protected void loadTextLinkExtractorFromXML(XML xml) {
        setIgnoreNofollow(xml.getBoolean("@ignoreNofollow", ignoreNofollow));
        setIgnoreLinkData(xml.getBoolean("@ignoreLinkData", ignoreLinkData));
        setCharset(xml.getString("@charset", charset));
        setParser(xml.getString("@parser", parser));
        setSchemes(xml.getDelimitedStringList("schemes", schemes));

        // load link selectors the deprecated way
        xml.checkDeprecated("dom", "linkSelector", false);
        var nodes = xml.getXMLList("dom");
        if (!nodes.isEmpty()) {
            linkSelectors.clear();
            for (XML node : nodes) {
                addLinkSelector(
                        node.getString("@selector", null),
                        node.getString("@extract", null));
            }
        }
        // Overwrite the link selectors with the new way if present
        var linkSelectXMLs = xml.getXMLList("linkSelector");
        if (!linkSelectXMLs.isEmpty()) {
            linkSelectors.clear();
            for (XML linkSelectXML : linkSelectXMLs) {
                addLinkSelector(
                        linkSelectXML.getString(".", null),
                        linkSelectXML.getString("@extract", null));
            }
        }

        // extract selector
        var extractSelList = xml.getStringList("extractSelector");
        if (!extractSelList.isEmpty()) {
            CollectionUtil.setAll(extractSelectors, extractSelList);
        }

        // no extract selector
        var noExtractSelList = xml.getStringList("noExtractSelector");
        if (!noExtractSelList.isEmpty()) {
            CollectionUtil.setAll(noExtractSelectors, noExtractSelList);
        }
    }

    @Override
    protected void saveTextLinkExtractorToXML(XML xml) {
        xml.setAttribute("ignoreNofollow", ignoreNofollow);
        xml.setAttribute("ignoreLinkData", ignoreLinkData);
        xml.setAttribute("charset", charset);
        xml.setAttribute("parser", parser);
        xml.addDelimitedElementList("schemes", schemes);
        for (Entry<String, String> en : linkSelectors.entrySet()) {
            xml.addElement("linkSelector")
                    .setAttribute("extract", en.getValue())
                    .setTextContent(en.getKey());
        }

        // extract selector
        xml.addElementList("extractSelector", extractSelectors);

        // no extract selector
        xml.addElementList("noExtractSelector", noExtractSelectors);
    }
}
