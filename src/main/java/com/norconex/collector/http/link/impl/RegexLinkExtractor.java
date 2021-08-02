/* Copyright 2017-2020 Norconex Inc.
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
package com.norconex.collector.http.link.impl;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.link.AbstractTextLinkExtractor;
import com.norconex.collector.http.link.Link;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;

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
 * <extractor class="com.norconex.collector.http.link.impl.RegexLinkExtractor"
 *     maxURLLength="(maximum URL length. Default is 2048)"
 *     charset="(supported character encoding)" >
 *
 *   {@nx.include com.norconex.collector.http.link.AbstractTextLinkExtractor@nx.xml.usage}
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
 * <extractor class="com.norconex.collector.http.link.impl.RegexLinkExtractor">
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
 * @author Pascal Essiembre
 * @since 2.7.0
 */
@SuppressWarnings("javadoc")
public class RegexLinkExtractor extends AbstractTextLinkExtractor {

    public static final String DEFAULT_CONTENT_TYPE_PATTERN = "text/.*";

    //TODO make buffer size and overlap size configurable
    //1MB: make configurable
    public static final int MAX_BUFFER_SIZE = 1024 * 1024;
    // max url leng is 2048 x 2 bytes x 2 for <a> anchor attributes.
    public static final int OVERLAP_SIZE = 2 * 2 * 2048;

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;

    private int maxURLLength = DEFAULT_MAX_URL_LENGTH;
    private String charset;
    private final Map<String, String> patterns = new ListOrderedMap<>();

    public RegexLinkExtractor() {
        super();
    }

    @Override
    public void extractTextLinks(
            Set<Link> links, HandlerDoc doc, Reader reader) throws IOException {

        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            sb.append((char) ch);
            if (sb.length() >= MAX_BUFFER_SIZE) {
                String content = sb.toString();
                extractLinks(content, doc.getReference(), links);
                sb.delete(0, sb.length() - OVERLAP_SIZE);
            }
        }
        String content = sb.toString();
        extractLinks(content, doc.getReference(), links);
        sb.setLength(0);
    }

    /**
     * Gets the maximum supported URL length.
     * @return maximum URL length
     */
    public int getMaxURLLength() {
        return maxURLLength;
    }
    /**
     * Sets the maximum supported URL length.
     * @param maxURLLength maximum URL length
     */
    public void setMaxURLLength(int maxURLLength) {
        this.maxURLLength = maxURLLength;
    }

    /**
     * Gets the character set of pages on which link extraction is performed.
     * Default is <code>null</code> (charset detection will be attempted).
     * @return character set to use, or <code>null</code>
     */
    public String getCharset() {
        return charset;
    }
    /**
     * Sets the character set of pages on which link extraction is performed.
     * Not specifying any (<code>null</code>) will attempt charset detection.
     * @param charset character set to use, or <code>null</code>
     */
    public void setCharset(String charset) {
        this.charset = charset;
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
        this.patterns.clear();
    }
    public void addPattern(String pattern) {
        this.patterns.put(pattern, null);
    }

    /**
     * Adds a URL pattern, with an optional replacement.
     * @param pattern a regular expression
     * @param replacement a regular expression replacement
     * @since 2.8.0
     */
    public void addPattern(String pattern, String replacement) {
        this.patterns.put(pattern, replacement);
    }

    private void extractLinks(
            String content, String referrer, Set<Link> links) {
        for (Entry<String, String> e: patterns.entrySet()) {
            String pattern = e.getKey();
            String repl = e.getValue();
            Matcher matcher = Pattern.compile(pattern).matcher(content);
            while (matcher.find()) {
                String url = matcher.group();
                if (StringUtils.isNotBlank(repl)) {
                    url = url.replaceFirst(pattern, repl);
                }
                url = HttpURL.toAbsolute(referrer, url);
                if (url == null) {
                    continue;
                }
                Link link = new Link(url);
                link.setReferrer(referrer);
                links.add(link);
            }
        }
    }

    @Override
    protected void loadTextLinkExtractorFromXML(XML xml) {
        setMaxURLLength(xml.getInteger("@maxURLLength", maxURLLength));
        setCharset(xml.getString("@charset", charset));

        List<XML> xmlPtrns = xml.getXMLList("linkExtractionPatterns/pattern");
        if (!xmlPtrns.isEmpty()) {
            clearPatterns();
            for (XML xmlPtrn : xmlPtrns) {
                String regex = xmlPtrn.getString("match");
                String repl = xmlPtrn.getString("replace");
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
        XML xmlPtrns = xml.addElement("linkExtractionPatterns");
        for (Entry<String, String> entry : patterns.entrySet()) {
            XML xmlPtrn = xmlPtrns.addElement("pattern");
            xmlPtrn.addElement("match", entry.getKey());
            xmlPtrn.addElement("replace", entry.getValue());
        }
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this,
                ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
