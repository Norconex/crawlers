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
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.doc.operations.link.LinkExtractor;
import com.norconex.crawler.web.doc.operations.url.WebUrlNormalizer;
import com.norconex.crawler.web.doc.operations.url.impl.GenericUrlNormalizer;
import com.norconex.importer.handler.CommonMatchers;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * A memory efficient HTML link extractor.
 * </p>
 * <p>
 * This link extractor uses regular expressions to extract links. It does
 * so on a chunk of text at a time, so that large files are not fully loaded
 * into memory. If you prefer a more flexible implementation that loads the
 * DOM model in memory to perform link extraction, consider using
 * {@link DomLinkExtractor}.
 * </p>
 *
 * <h3>Applicable documents</h3>
 * <p>
 * By default, this extractor only will be applied on documents matching
 * one of these content types:
 * </p>
 * {@nx.include com.norconex.importer.handler.CommonRestrictions#htmlContentTypes}
 * <p>
 * You can specify your own content types or other restrictions with
 * {@link #setRestrictions(List)}.
 * Make sure they represent a file with HTML-like markup tags containing URLs.
 * For documents that are just
 * too different, consider implementing your own {@link LinkExtractor} instead.
 * Removing the default values and define no content types will have for effect
 * to try to extract URLs from all files (usually a bad idea).
 * </p>
 *
 * <h3>Tags attributes</h3>
 * URLs are assumed to be contained within valid tags or tag attributes.
 * The default tags and attributes used are (tag.attribute):
 * <pre>
 * a.href, frame.src, iframe.src, img.src, meta.http-equiv
 * </pre>
 * You can specify your own set of tags and attributes to have
 * different ones used for extracting URLs. For an elaborated set, you can
 * combine the above with your own list or use any of the following
 * suggestions (tag.attribute):
 * <pre>
 * applet.archive,   applet.codebase,  area.href,         audio.src,
 * base.href,        blockquote.cite,  body.background,   button.formaction,
 * command.icon,     del.cite,         embed.src,         form.action,
 * frame.longdesc,   head.profile,     html.manifest,     iframe.longdesc,
 * img.longdesc,     img.usemap,       input.formaction,  input.src,
 * input.usemap,     ins.cite,         link.href,         object.archive,
 * object.classid,   object.codebase,  object.data,       object.usemap,
 * q.cite,           script.src,       source.src,        video.poster,
 * video.src
 * </pre>
 * <p>
 * The <code>meta.http-equiv</code> is treated differently.  Only if the
 * "http-equiv" value is "refresh" and a "content" attribute with a URL exist
 * that it will be extracted.  "object" and "applet" can have multiple URLs.
 * </p>
 *
 * <p>
 * It is possible to identify a tag only as the holder of
 * a URL (without attributes). The tag body value will be used as the URL.
 * </p>
 *
 * <h3>Referrer data</h3>
 * <p>
 * Some "referrer" information is derived from the each link and stored as
 * metadata in the document they point to.
 * These may vary for each link, but they are normally prefixed with
 * {@link WebDocMetadata#REFERRER_LINK_PREFIX}.
 * </p>
 * <p>
 * The referrer data is always stored (was optional before).
 * </p>
 *
 * <h3>Character encoding</h3>
 * <p>This extractor will by default <i>attempt</i> to
 * detect the encoding of the a page when extracting links and
 * referrer information. If no charset could be detected, it falls back to
 * UTF-8. It is also possible to dictate which encoding to use with
 * {@link #setCharset(String)}.
 * </p>
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
 * <h3>URL Fragments</h3>
 * <p>This extractor preserves hashtag characters (#) found
 * in URLs and every characters after it. It relies on the implementation
 * of {@link WebUrlNormalizer} to strip it if need be.
 * {@link GenericUrlNormalizer} is now always invoked by default, and the
 * default set of rules defined for it will remove fragments.
 * </p>
 *
 * <p>
 * The URL specification says hashtags
 * are used to represent fragments only. That is, to quickly jump to a specific
 * section of the page the URL represents. Under normal circumstances,
 * keeping the URL fragments usually leads to duplicates documents being fetched
 * (same URL but different fragment) and they should be stripped. Unfortunately,
 * there are sites not following the URL standard and using hashtags as a
 * regular part of a URL (i.e. different hashtags point to different web pages).
 * It may be essential when crawling these sites to keep the URL fragments.
 * This can be done by making sure the URL normalizer does not strip them.
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
 * <h3>HTML/XML Comments</h3>
 * <p>URLs found in &lt;!-- comments --&gt; are no longer
 * extracted by default. To enable URL extraction from comments, use
 * {@link #setCommentsEnabled(boolean)}
 * </p>
 *
 * <h3>Extract links in certain parts only</h3>
 * <p>You can identify portions of a document where links
 * should be extracted or ignored with
 * {@link #setExtractBetweens(List)} and
 * {@link #setNoExtractBetweens(List)}. Eligible content for link
 * extraction is identified first, and content to exclude is done on that
 * subset.
 * </p>
 * <p>You can further limit link extraction to specific
 * area by using
 * <a href="https://jsoup.org/cookbook/extracting-data/selector-syntax">selector-syntax</a>
 * to do so, with
 * {@link #setExtractSelectors(List)} and
 * {@link #setNoExtractSelectors(List)}.
 * </p>
 *
 * {@nx.xml.usage
 * <extractor class="com.norconex.crawler.web.doc.operations.link.impl.HtmlLinkExtractor"
 *     maxURLLength="(maximum URL length. Default is 2048)"
 *     ignoreNofollow="[false|true]"
 *     ignoreLinkData="[false|true]"
 *     commentsEnabled="[false|true]"
 *     charset="(supported character encoding)">
 *
 *   {@nx.include com.norconex.crawler.web.doc.operations.link.AbstractTextLinkExtractor@nx.xml.usage}
 *
 *   <schemes>
 *     (CSV list of URI scheme for which to perform link extraction.
 *      leave blank or remove tag to use defaults.)
 *   </schemes>
 *
 *   <!-- Which tags and attributes hold the URLs to extract. -->
 *   <tags>
 *     <tag name="(tag name)" attribute="(tag attribute)" />
 *     <!-- you can have multiple tag entries -->
 *   </tags>
 *
 *   <!-- Only extract URLs from the following text portions. -->
 *   <extractBetween ignoreCase="[false|true]">
 *     <start>(regex)</start>
 *     <end>(regex)</end>
 *   </extractBetween>
 *   <!-- you can have multiple extractBetween entries -->
 *
 *   <!-- Do not extract URLs from the following text portions. -->
 *   <noExtractBetween ignoreCase="[false|true]">
 *     <start>(regex)</start>
 *     <end>(regex)</end>
 *   </noExtractBetween>
 *   <!-- you can have multiple noExtractBetween entries -->
 *
 *   <!-- Only extract URLs matching the following selectors. -->
 *   <extractSelector>(selector)</extractSelector>
 *   <!-- you can have multiple extractSelector entries -->
 *
 *   <!-- Do not extract URLs matching the following selectors. -->
 *   <noExtractSelector>(selector)</noExtractSelector>
 *   <!-- you can have multiple noExtractSelector entries -->
 *
 * </extractor>
 * }
 *
 * {@nx.xml.example
 * <extractor class="com.norconex.crawler.web.doc.operations.link.impl.HtmlLinkExtractor">
 *   <tags>
 *     <tag name="a" attribute="href" />
 *     <tag name="frame" attribute="src" />
 *     <tag name="iframe" attribute="src" />
 *     <tag name="img" attribute="src" />
 *     <tag name="meta" attribute="http-equiv" />
 *     <tag name="script" attribute="src" />
 *   </tags>
 * </extractor>
 * }
 *
 * <p>
 * The above example adds URLs to JavaScript files to the list of URLs to be
 * extracted.
 * </p>
 */
@SuppressWarnings("javadoc")
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
     * @param contentTypeMatcher content type matcher
     * @return content type matcher
     */
    private final TextMatcher contentTypeMatcher =
            CommonMatchers.htmlContentTypes();

    /**
     * Matcher of one or more fields to use as the source of content to
     * extract links from, instead of the document content.
     * @param fieldMatcher field matcher
     * @return field matcher
     */
    private final TextMatcher fieldMatcher = new TextMatcher();

    /**
     * The maximum supported URL length. Longer URLs are ignored.
     * @param maxURLLength maximum URL length
     * @return maximum URL length
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
     * @param ignoreNofollow whether to ignore "nofollow" directives
     * @return <code>true</code> if ignoring "nofollow" directives
     */
    private boolean ignoreNofollow;

    /**
     * Gets whether to ignore extra data associated with a link.
     * @param ignoreLinkData <code>true</code> to ignore.
     * @return <code>true</code> to ignore.
     */
    private boolean ignoreLinkData;

    /**
     * The character set to use for pages on which link extraction is performed.
     * When <code>null</code> (default), character set detection will be
     * attempted.
     * @param charset character set to use, or <code>null</code>
     * @return character set to use, or <code>null</code>
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
     * @return <code>true</code> if links should be extracted from comments.
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
     */
    public HtmlLinkExtractorConfig setExtractBetweens(
            List<RegexPair> betweens
    ) {
        CollectionUtil.setAll(extractBetweens, betweens);
        return this;
    }

    /**
     * Adds patterns delimiting a portion of a document to be considered
     * for link extraction.
     * @param start pattern matching start of text portion
     * @param end pattern matching end of text portion
     * @param ignoreCase whether the patterns are case sensitive or not
     */
    public HtmlLinkExtractorConfig addExtractBetween(
            String start, String end, boolean ignoreCase
    ) {
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
     */
    public HtmlLinkExtractorConfig setNoExtractBetweens(
            List<RegexPair> betweens
    ) {
        CollectionUtil.setAll(noExtractBetweens, betweens);
        return this;
    }

    /**
     * Adds patterns delimiting a portion of a document to be excluded
     * from link extraction.
     * @param start pattern matching start of text portion
     * @param end pattern matching end of text portion
     * @param ignoreCase whether the patterns are case sensitive or not
     */
    public HtmlLinkExtractorConfig addNoExtractBetween(
            String start, String end, boolean ignoreCase
    ) {
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
     */
    public HtmlLinkExtractorConfig setExtractSelectors(
            List<String> selectors
    ) {
        CollectionUtil.setAll(extractSelectors, selectors);
        return this;
    }

    /**
     * Adds selectors matching the portions of a document to be considered
     * for link extraction.
     * @param selectors selectors
     */
    public HtmlLinkExtractorConfig addExtractSelectors(
            List<String> selectors
    ) {
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
     */
    public HtmlLinkExtractorConfig setNoExtractSelectors(
            List<String> selectors
    ) {
        CollectionUtil.setAll(noExtractSelectors, selectors);
        return this;
    }

    /**
     * Adds selectors matching the portions of a document to be excluded
     * from link extraction.
     * @param selectors selectors
     */
    public HtmlLinkExtractorConfig addNoExtractSelectors(
            List<String> selectors
    ) {
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
     * @param contentTypeMatcher content type matcher
     * @return this
     */
    public HtmlLinkExtractorConfig setContentTypeMatcher(TextMatcher matcher) {
        contentTypeMatcher.copyFrom(matcher);
        return this;
    }

    //--- Public methods -------------------------------------------------------

    public synchronized HtmlLinkExtractorConfig addLinkTag(
            String tagName, String attribute
    ) {
        tagAttribs.add(tagName, attribute);
        return this;
    }

    public synchronized HtmlLinkExtractorConfig removeLinkTag(
            String tagName, String attribute
    ) {
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
                @JsonProperty(value = "ignoreCase") boolean ignoreCase
        ) {
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
