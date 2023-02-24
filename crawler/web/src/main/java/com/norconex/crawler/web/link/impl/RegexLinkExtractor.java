/* Copyright 2017-2023 Norconex Inc.
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

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.web.doc.HttpDocMetadata;
import com.norconex.crawler.web.link.AbstractTextLinkExtractor;
import com.norconex.crawler.web.link.Link;
import com.norconex.importer.handler.HandlerDoc;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * Link extractor using regular expressions to extract links found in text
 * documents. Relative links are resolved to the document URL.
 * For HTML documents, it is best advised to use the
 * {@link HtmlLinkExtractor} or {@link DOMLinkExtractor},
 * which addresses many cases specific to HTML.
 * </p>
 *
 * <h3>Applicable documents</h3>
 * <p>
 * By default, this extractor will extract URLs only in documents having
 * their content type matching this regular expression:
 * </p>
 * <pre>
 * text/.*
 * </pre>
 * <p>
 * You can specify your own restrictions using {@link #setRestrictions(List)},
 * but make sure they represent text files.
 * </p>
 *
 * <h3>Referrer data</h3>
 * <p>
 * The following referrer information is stored as metadata in each document
 * represented by the extracted URLs:
 * </p>
 * <ul>
 *   <li><b>Referrer reference:</b> The reference (URL) of the page where the
 *   link to a document was found.  Metadata value is
 *   {@link HttpDocMetadata#REFERRER_REFERENCE}.</li>
 * </ul>
 *
 * <h3>Character encoding</h3>
 * <p>This extractor will by default <i>attempt</i> to
 * detect the encoding of the a page when extracting links and
 * referrer information. If no charset could be detected, it falls back to
 * UTF-8. It is also possible to dictate which encoding to use with
 * {@link #setCharset(String)}.
 * </p>
 *
 * {@nx.xml.usage
 * <extractor class="com.norconex.crawler.web.link.impl.RegexLinkExtractor"
 *     maxURLLength="(maximum URL length. Default is 2048)"
 *     charset="(supported character encoding)" >
 *
 *   {@nx.include com.norconex.crawler.web.link.AbstractTextLinkExtractor@nx.xml.usage}
 *
 *   <!-- Patterns for URLs to extract -->
 *   <linkExtractionPatterns>
 *     <pattern>
 *       <match>(regular expression)</match>
 *       <replace>(optional regex replacement)</replace>
 *     </pattern>
 *     <!-- you can have multiple pattern entries -->
 *   </linkExtractionPatterns>
 * </extractor>
 * }
 *
 * {@nx.xml.example
 * <extractor class="com.norconex.crawler.web.link.impl.RegexLinkExtractor">
 *   <linkExtractionPatterns>
 *     <pattern>
 *       <match>\[(\d+)\]</match>
 *       <replace>http://www.example.com/page?id=$1</replace>
 *     </pattern>
 *   </linkExtractionPatterns>
 * </extractor>
 * }
 * <p>
 * The above example extracts page "ids" contained in square brackets and
 * add them to a custom URL.
 * </p>
 *
 * @since 2.7.0
 */
@SuppressWarnings("javadoc")
@Data
public class RegexLinkExtractor extends AbstractTextLinkExtractor {

    public static final String DEFAULT_CONTENT_TYPE_PATTERN = "text/.*";

    //TODO make buffer size and overlap size configurable
    //1MB: make configurable
    public static final int MAX_BUFFER_SIZE = 1024 * 1024;
    // max url leng is 2048 x 2 bytes x 2 for <a> anchor attributes.
    public static final int OVERLAP_SIZE = 2 * 2 * 2048;

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;

    /**
     * The maximum supported URL length.
     * Default is {@value #DEFAULT_MAX_URL_LENGTH}.
     * @param maxURLLength maximum URL length
     * @return maximum URL length
     */
    private int maxURLLength = DEFAULT_MAX_URL_LENGTH;
    /**
     * Gets the character set of pages on which link extraction is performed.
     * Default is <code>null</code> (charset detection will be attempted).
     * @param charset character set to use, or <code>null</code>
     * @return character set to use, or <code>null</code>
     */
    private String charset;

    @Setter(value = AccessLevel.NONE)
    @Getter(value = AccessLevel.NONE)
    private final Map<String, String> patterns = new ListOrderedMap<>();

    @Override
    public void extractTextLinks(
            Set<Link> links, HandlerDoc doc, Reader reader) throws IOException {

        var sb = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            sb.append((char) ch);
            if (sb.length() >= MAX_BUFFER_SIZE) {
                var content = sb.toString();
                extractLinks(content, doc.getReference(), links);
                sb.delete(0, sb.length() - OVERLAP_SIZE);
            }
        }
        var content = sb.toString();
        extractLinks(content, doc.getReference(), links);
        sb.setLength(0);
    }

    public List<String> getPatterns() {
        return new ArrayList<>(patterns.keySet());
    }

    /**
     * Gets a pattern replacement.
     * @param pattern the pattern for which to obtain its replacement
     * @return pattern replacement or <code>null</code> (no replacement)
     * @since 2.8.0
     */
    public String getPatternReplacement(String pattern) {
        return patterns.get(pattern);
    }
    public void clearPatterns() {
        patterns.clear();
    }
    public void addPattern(String pattern) {
        patterns.put(pattern, null);
    }

    /**
     * Adds a URL pattern, with an optional replacement.
     * @param pattern a regular expression
     * @param replacement a regular expression replacement
     * @since 2.8.0
     */
    public void addPattern(String pattern, String replacement) {
        patterns.put(pattern, replacement);
    }

    private void extractLinks(
            String content, String referrer, Set<Link> links) {
        for (Entry<String, String> e: patterns.entrySet()) {
            var pattern = e.getKey();
            var repl = e.getValue();
            var matcher = Pattern.compile(pattern).matcher(content);
            while (matcher.find()) {
                var url = matcher.group();
                if (StringUtils.isNotBlank(repl)) {
                    url = url.replaceFirst(pattern, repl);
                }
                url = HttpURL.toAbsolute(referrer, url);
                if (url == null) {
                    continue;
                }
                var link = new Link(url);
                link.setReferrer(referrer);
                links.add(link);
            }
        }
    }

    @Override
    protected void loadTextLinkExtractorFromXML(XML xml) {
        setMaxURLLength(xml.getInteger("@maxURLLength", maxURLLength));
        setCharset(xml.getString("@charset", charset));

        var xmlPtrns = xml.getXMLList("linkExtractionPatterns/pattern");
        if (!xmlPtrns.isEmpty()) {
            clearPatterns();
            for (XML xmlPtrn : xmlPtrns) {
                var regex = xmlPtrn.getString("match");
                var repl = xmlPtrn.getString("replace");
                if (StringUtils.isNotBlank(regex)) {
                    addPattern(regex, repl);
                }
            }
        }
    }


    @Override
    protected void saveTextLinkExtractorToXML(XML xml) {
        xml.setAttribute("maxURLLength", maxURLLength);
        xml.setAttribute("charset", charset);
        // Tags
        var xmlPtrns = xml.addElement("linkExtractionPatterns");
        for (Entry<String, String> entry : patterns.entrySet()) {
            var xmlPtrn = xmlPtrns.addElement("pattern");
            xmlPtrn.addElement("match", entry.getKey());
            xmlPtrn.addElement("replace", entry.getValue());
        }
    }
}
