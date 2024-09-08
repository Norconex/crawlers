/* Copyright 2014-2024 Norconex Inc.
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.CommonMatchers;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link HtmlLinkExtractor}.
 * </p>
 */
@Data
@Accessors(chain = true)
public class HtmlLinkExtractorConfig {

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;

    /** Default supported URL schemes (http, https, and ftp). */
    public static final List<String> DEFAULT_SCHEMES =
            List.of("http", "https", "ftp");

    public static final String HTTP_EQUIV = "http-equiv";

    //--- Properties -----------------------------------------------------------

    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default is
     * {@link CommonMatchers#HTML_CONTENT_TYPES}.
     */
    private final TextMatcher contentTypeMatcher =
            CommonMatchers.htmlContentTypes();

    private final PropertyMatchers restrictions = new PropertyMatchers();

    /**
     * Matcher of one or more fields to use as the source of content to
     * extract links from, instead of the document content.
     */
    private final TextMatcher fieldMatcher = new TextMatcher();

    /**
     * The maximum supported URL length. Longer URLs are ignored.
     */
    private int maxURLLength = DEFAULT_MAX_URL_LENGTH;

    /**
     * Whether to ignore "nofollow" directives on HTML links. An example
     * of such links:
     * <pre>
     * &lt;a href="https://yoursite.com/doNotCrawl.html" rel="nofollow"&gt;
     *   By default this link won't be crawled.
     * &lt;/a&gt;
     * </pre>
     */
    private boolean ignoreNofollow;

    /**
     * Gets whether to ignore extra data associated with a link.
     */
    private boolean ignoreLinkData;

    /**
     * The character set to use for pages on which link extraction is performed.
     * When <code>null</code> (default), character set detection will be
     * attempted.
     */
    private Charset charset;

    /**
     * Gets whether links should be extracted from comments. Comment example:
     * <pre>
     * &lt;!--
     * By default, this URL won't be crawled:
     * &lt;a href="https://yoursite.com/somepage.html"&gt;Some URL&lt;/a&gt;
     * --&gt;
     * </pre>
     */
    private boolean commentsEnabled;

    private final Properties tagAttribs = new Properties(true);
    private final List<String> schemes = new ArrayList<>(DEFAULT_SCHEMES);
    private final List<String> extractSelectors = new ArrayList<>();
    private final List<String> noExtractSelectors = new ArrayList<>();
    private final List<RegexPair> extractBetweens = new ArrayList<>();
    private final List<RegexPair> noExtractBetweens = new ArrayList<>();

    public HtmlLinkExtractorConfig() {
        // default tags/attributes used to extract data.
        addLinkTag("a", "href");
        addLinkTag("frame", "src");
        addLinkTag("iframe", "src");
        addLinkTag("img", "src");
        addLinkTag("meta", HTTP_EQUIV);
    }

    /**
     * Gets the patterns delimiting the portions of a document to be considered
     * for link extraction.
     * @return extract between patterns
     */
    public List<RegexPair> getExtractBetweens() {
        return Collections.unmodifiableList(extractBetweens);
    }

    /**
     * Sets the patterns delimiting the portions of a document to be considered
     * for link extraction.
     * @param betweens extract between patterns
     * @return this
     */
    public HtmlLinkExtractorConfig setExtractBetweens(
            List<RegexPair> betweens) {
        CollectionUtil.setAll(extractBetweens, betweens);
        return this;
    }

    /**
     * Adds patterns delimiting a portion of a document to be considered
     * for link extraction.
     * @param start pattern matching start of text portion
     * @param end pattern matching end of text portion
     * @param ignoreCase whether the patterns are case sensitive or not
     * @return this
     */
    public HtmlLinkExtractorConfig addExtractBetween(
            String start, String end, boolean ignoreCase) {
        extractBetweens.add(new RegexPair(start, end, ignoreCase));
        return this;
    }

    /**
     * Gets the patterns delimiting the portions of a document to be excluded
     * from link extraction.
     * @return extract between patterns
     */
    public List<RegexPair> getNoExtractBetweens() {
        return Collections.unmodifiableList(noExtractBetweens);
    }

    /**
     * Sets the patterns delimiting the portions of a document to be excluded
     * from link extraction.
     * @param betweens extract between patterns
     * @return this
     */
    public HtmlLinkExtractorConfig setNoExtractBetweens(
            List<RegexPair> betweens) {
        CollectionUtil.setAll(noExtractBetweens, betweens);
        return this;
    }

    /**
     * Adds patterns delimiting a portion of a document to be excluded
     * from link extraction.
     * @param start pattern matching start of text portion
     * @param end pattern matching end of text portion
     * @param ignoreCase whether the patterns are case sensitive or not
     * @return this
     */
    public HtmlLinkExtractorConfig addNoExtractBetween(
            String start, String end, boolean ignoreCase) {
        noExtractBetweens.add(new RegexPair(start, end, ignoreCase));
        return this;
    }

    /**
     * Gets the selectors matching the portions of a document to be considered
     * for link extraction.
     * @return selectors
     */
    public List<String> getExtractSelectors() {
        return Collections.unmodifiableList(extractSelectors);
    }

    /**
     * Sets the selectors matching the portions of a document to be considered
     * for link extraction.
     * @param selectors selectors
     * @return this
     */
    public HtmlLinkExtractorConfig setExtractSelectors(
            List<String> selectors) {
        CollectionUtil.setAll(extractSelectors, selectors);
        return this;
    }

    /**
     * Adds selectors matching the portions of a document to be considered
     * for link extraction.
     * @param selectors selectors
     * @return this
     */
    public HtmlLinkExtractorConfig addExtractSelectors(
            List<String> selectors) {
        extractSelectors.addAll(selectors);
        return this;
    }

    /**
     * Gets the selectors matching the portions of a document to be excluded
     * from link extraction.
     * @return selectors
     */
    public List<String> getNoExtractSelectors() {
        return Collections.unmodifiableList(noExtractSelectors);
    }

    /**
     * Sets the selectors matching the portions of a document to be excluded
     * from link extraction.
     * @param selectors selectors
     * @return this
     */
    public HtmlLinkExtractorConfig setNoExtractSelectors(
            List<String> selectors) {
        CollectionUtil.setAll(noExtractSelectors, selectors);
        return this;
    }

    /**
     * Adds selectors matching the portions of a document to be excluded
     * from link extraction.
     * @param selectors selectors
     * @return this
     */
    public HtmlLinkExtractorConfig addNoExtractSelectors(
            List<String> selectors) {
        noExtractSelectors.addAll(selectors);
        return this;
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
     * @return this
     */
    public HtmlLinkExtractorConfig setSchemes(List<String> schemes) {
        CollectionUtil.setAll(this.schemes, schemes);
        return this;
    }

    public HtmlLinkExtractorConfig setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        return this;
    }

    /**
     * The matcher of content types to apply link extraction on. No attempt to
     * extract links from any other content types will be made. Default is
     * {@link CommonMatchers#HTML_CONTENT_TYPES}.
     * @param matcher content type matcher
     * @return this
     */
    public HtmlLinkExtractorConfig setContentTypeMatcher(TextMatcher matcher) {
        contentTypeMatcher.copyFrom(matcher);
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

    public synchronized HtmlLinkExtractorConfig addLinkTag(
            String tagName, String attribute) {
        tagAttribs.add(tagName, attribute);
        return this;
    }

    public synchronized HtmlLinkExtractorConfig removeLinkTag(
            String tagName, String attribute) {
        if (attribute == null) {
            tagAttribs.remove(tagName);
        } else {
            var values = tagAttribs.getStrings(tagName);
            values.remove(attribute);
            if (values.isEmpty()) {
                tagAttribs.remove(tagName);
            } else {
                tagAttribs.setList(tagName, values);
            }
        }
        return this;
    }

    public synchronized void clearLinkTags() {
        tagAttribs.clear();
    }

    //--- Inner Classes --------------------------------------------------------

    //MAYBE: make standalone class?
    @Data
    public static class RegexPair {
        private final String start;
        private final String end;
        private final boolean ignoreCase;

        @JsonCreator
        public RegexPair(
                @JsonProperty(value = "start") String start,
                @JsonProperty(value = "end") String end,
                @JsonProperty(value = "ignoreCase") boolean ignoreCase) {
            this.start = start;
            this.end = end;
            this.ignoreCase = ignoreCase;
        }

        public String getStart() {
            return start;
        }

        public String getEnd() {
            return end;
        }

        public boolean isIgnoreCase() {
            return ignoreCase;
        }
    }
}
