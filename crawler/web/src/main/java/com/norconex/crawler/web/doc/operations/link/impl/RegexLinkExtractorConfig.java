/* Copyright 2017-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.link.impl;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.web.doc.WebDocMetadata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>
 * Link extractor using regular expressions to extract links found in text
 * documents. Relative links are resolved to the document URL.
 * For HTML documents, it is best advised to use the
 * {@link HtmlLinkExtractor} or {@link DomLinkExtractor},
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
 *   {@link WebDocMetadata#REFERRER_REFERENCE}.</li>
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
 * <extractor class="com.norconex.crawler.web.doc.operations.link.impl.RegexLinkExtractor"
 *     maxURLLength="(maximum URL length. Default is 2048)"
 *     charset="(supported character encoding)" >
 *
 *   {@nx.include com.norconex.crawler.web.doc.operations.link.AbstractTextLinkExtractor@nx.xml.usage}
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
 * <extractor class="com.norconex.crawler.web.doc.operations.link.impl.RegexLinkExtractor">
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
@Accessors(chain = true)
public class RegexLinkExtractorConfig {
    //TODO add fieldMatcher and contentTypeMatcher?

    public static final String DEFAULT_CONTENT_TYPE_PATTERN = "text/.*";

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ExtractionPattern {
        private String match;
        private String replace;
    }

    /**
     * The maximum supported URL length.
     * Default is {@value #DEFAULT_MAX_URL_LENGTH}.
     * @param maxUrlLength maximum URL length
     * @return maximum URL length
     */
    private int maxUrlLength = DEFAULT_MAX_URL_LENGTH;

    /**
     * Gets the character set of pages on which link extraction is performed.
     * Default is <code>null</code> (charset detection will be attempted).
     * @param charset character set to use, or <code>null</code>
     * @return character set to use, or <code>null</code>
     */
    private Charset charset;

    private final List<ExtractionPattern> patterns = new ArrayList<>();

    private final PropertyMatchers restrictions = new PropertyMatchers();

    /**
     * Matcher of one or more fields to use as the source of content to
     * extract links from, instead of the document content.
     * @param fieldMatcher field matcher
     * @return field matcher
     */
    private final TextMatcher fieldMatcher = new TextMatcher();


    public List<ExtractionPattern> getPatterns() {
        return Collections.unmodifiableList(patterns);
    }
    public RegexLinkExtractorConfig setPatterns(
            List<ExtractionPattern> patterns) {
        CollectionUtil.setAll(this.patterns, patterns);
        return this;
    }
    public RegexLinkExtractorConfig clearPatterns() {
        patterns.clear();
        return this;
    }

    /**
     * Clears all restrictions.
     */
    public void clearRestrictions() {
        restrictions.clear();
    }
    /**
     * Gets all restrictions
     * @return the restrictions
     */
    public PropertyMatchers getRestrictions() {
        return restrictions;
    }
}
