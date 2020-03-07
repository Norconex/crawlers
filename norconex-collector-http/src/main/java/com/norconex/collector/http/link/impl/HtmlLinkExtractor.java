/* Copyright 2014-2020 Norconex Inc.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.link.AbstractLinkExtractor;
import com.norconex.collector.http.link.ILinkExtractor;
import com.norconex.collector.http.link.Link;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.collector.http.url.impl.GenericURLNormalizer;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.CharsetUtil;

/**
 * <p>
 * Html link extractor for URLs found in HTML and possibly other text files.
 * </p>
 * <p>
 * This link extractor uses regular expressions to extract links. It does
 * so on a chunk of text at a time, so that large files are not fully loaded
 * into memory. If you prefer a more flexible implementation that loads the
 * DOM model in memory to perform link extraction, consider using
 * {@link DOMLinkExtractor}.
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
 * too different, consider implementing your own {@link ILinkExtractor} instead.
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
 * <p>The <code>meta.http-equiv</code> is treated differently.  Only if the
 * "http-equiv" value is refresh and a "content" tag with a URL exist that it
 * will be extracted.  "object" and "applet" can have multiple URLs.</p>
 *
 * <p>
 * <b>Since 2.2.0</b>, it is possible to identify a tag only as the holder of
 * a URL (without attributes). The tag body value will be used as the URL.
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
 *   <li><b>Referrer link tag:</b> The tag and attribute names of the link
 *   that contained the document reference (URL) in referrer's content.
 *   Metadata value is
 *   {@link HttpDocMetadata#REFERRER_LINK_TAG}.</li>
 *   <li><b>Referrer link text:</b> The text between the
 *   <code>&lt;a href=""&gt;&lt;/a&gt;</code> tags of the referrer document.
 *   Can be useful to help establish better document titles.
 *   Metadata value is
 *   {@link HttpDocMetadata#REFERRER_LINK_TEXT}.</li>
 *   <li><b>Referrer link title:</b> The <code>title</code> attribute of the
     link that contained the document reference (URL) in referrer's content.
 *   Can also be useful to help establish better document titles.
 *   Metadata value is
 *   {@link HttpDocMetadata#REFERRER_LINK_TITLE}.</li>
 * </ul>
 * <p>
 * <b>Since 2.6.0</b>, the referrer data is always stored (was optional before).
 * </p>
 *
 * <h3>Character encoding</h3>
 * <p><b>Since 2.4.0</b>, this extractor will by default <i>attempt</i> to
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
 * <p>
 *
 * <h3>URL Fragments</h3>
 * <p><b>Since 2.3.0</b>, this extractor preserves hashtag characters (#) found
 * in URLs and every characters after it. It relies on the implementation
 * of {@link IURLNormalizer} to strip it if need be.
 * {@link GenericURLNormalizer} is now always invoked by default, and the
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
 * <h3>URL Schemes</h3>
 * <p><b>Since 2.4.0</b>, only valid
 * <a href="https://en.wikipedia.org/wiki/Uniform_Resource_Identifier#Syntax">
 * schemes</a> are extracted for absolute URLs. By default, those are
 * <code>http</code>, <code>https</code>, and <code>ftp</code>. You can
 * specify your own list of supported protocols with
 * {@link #setSchemes(String[])}.
 * </p>
 *
 * <h3>HTML/XML Comments</h3>
 * <p><b>Since 2.6.0</b>, URLs found in &lt;!-- comments --&gt; are no longer
 * extracted by default. To enable URL extraction from comments, use
 * {@link #setCommentsEnabled(boolean)}
 * </p>
 *
 * <h3>Extract links in certain parts only</h3>
 * <p><b>Since 2.8.0</b>, you can identify portions of a document where links
 * should be extracted or ignored with
 * {@link #setExtractBetweens(RegexPair...)} and
 * {@link #setNoExtractBetweens(RegexPair...)}. Eligible content for link
 * extraction is identified first, and content to exclude is done on that
 * subset.
 * </p>
 * <p><b>Since 2.9.0</b>, you can further limit link extraction to specific
 * area by using
 * <a href="https://jsoup.org/cookbook/extracting-data/selector-syntax">selector-syntax</a>
 * to do so, with
 * {@link #setExtractSelectors(String...)} and
 * {@link #setNoExtractSelectors(String...)}.
 * </p>
 *
 * {@nx.xml.usage
 * <extractor class="com.norconex.collector.http.link.impl.HtmlLinkExtractor"
 *     maxURLLength="(maximum URL length. Default is 2048)"
 *     ignoreNofollow="[false|true]"
 *     commentsEnabled="[false|true]"
 *     charset="(supported character encoding)">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
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
 *   <extractBetween caseSensitive="[false|true]">
 *     <start>(regex)</start>
 *     <end>(regex)</end>
 *   </extractBetween>
 *   <!-- you can have multiple extractBetween entries -->
 *
 *   <!-- Do not extract URLs from the following text portions. -->
 *   <noExtractBetween caseSensitive="[false|true]">
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
 * <extractor class="com.norconex.collector.http.link.impl.HtmlLinkExtractor">
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
 * @author Pascal Essiembre
 * @since 3.0.0 (refactored from GenericLinkExtractor)
 */
@SuppressWarnings("javadoc")
public class HtmlLinkExtractor extends AbstractLinkExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(
            HtmlLinkExtractor.class);

    //TODO give option to match fields where to grab dom content.

    //TODO make buffer size and overlap size configurable
    //1MB: make configurable
    public static final int MAX_BUFFER_SIZE = 1024 * 1024;
    // max url leng is 2048 x 2 bytes x 2 for <a> anchor attributes.
    public static final int OVERLAP_SIZE = 2 * 2 * 2048;

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;

    private static final List<String> DEFAULT_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("http", "https", "ftp"));

    private static final int PATTERN_FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final int LOGGING_MAX_URL_LENGTH = 200;

    private final List<String> schemes = new ArrayList<>(DEFAULT_SCHEMES);
    private int maxURLLength = DEFAULT_MAX_URL_LENGTH;
    private boolean ignoreNofollow;
    private final Properties tagAttribs = new Properties(true);
    @HashCodeExclude
    @EqualsExclude
    private Pattern tagPattern;
    private String charset;
    private boolean commentsEnabled;

    private final List<String> extractSelectors = new ArrayList<>();
    private final List<String> noExtractSelectors = new ArrayList<>();
    private final List<RegexPair> extractBetweens = new ArrayList<>();
    private final List<RegexPair> noExtractBetweens = new ArrayList<>();

    public HtmlLinkExtractor() {
        super();

        // default content type this extractor applies to
        setRestrictions(CommonRestrictions.htmlContentTypes(
                DocMetadata.CONTENT_TYPE));

        // default tags/attributes used to extract data.
        addLinkTag("a", "href");
        addLinkTag("frame", "src");
        addLinkTag("iframe", "src");
        addLinkTag("img", "src");
        addLinkTag("meta", "http-equiv");
    }

    private static final Pattern BASE_HREF_PATTERN = Pattern.compile(
            "<base[^<]+?href\\s*=\\s*([\"']{0,1})(.*?)\\1", PATTERN_FLAGS);


    @Override
    public void extractLinks(Set<Link> links, CrawlDoc doc,
            ParseState parseState) throws IOException {

        String sourceCharset =
                CharsetUtil.detectCharsetIfNotBlank(getCharset(), doc);

        Referer referer = new Referer(doc.getReference());

        StringBuilder sb = new StringBuilder();
        Reader r = new BufferedReader(
                new InputStreamReader(doc.getInputStream(), sourceCharset));
        int ch;
        boolean firstChunk = true;
        while ((ch = r.read()) != -1) {
            sb.append((char) ch);
            if (sb.length() >= MAX_BUFFER_SIZE) {
                String content = sb.toString();
                referer = adjustReferer(content, referer, firstChunk);
                firstChunk = false;
                extractLinks(content, referer, links);
                sb.delete(0, sb.length() - OVERLAP_SIZE);
            }
        }
        String content = sb.toString();
        referer = adjustReferer(content, referer, firstChunk);
        extractLinks(content, referer, links);
        sb.setLength(0);
    }

    @Override
    protected boolean acceptParseState(ParseState parseState) {
        return ParseState.isPre(parseState);
    }

    private Referer adjustReferer(
            final String content, final Referer referer,
            final boolean firstChunk) {
        Referer ref = referer;
        if (firstChunk) {
            Matcher matcher = BASE_HREF_PATTERN.matcher(content);
            if (matcher.find()) {
                String reference = matcher.group(2);
                if (StringUtils.isNotBlank(reference)) {
                    reference = toCleanAbsoluteURL(referer, reference);
                    ref = new Referer(reference);
                }
            }
        }
        return ref;
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
     * Gets the patterns delimiting the portions of a document to be considered
     * for link extraction.
     * @return extract between patterns
     * @since 2.8.0
     */
    public List<RegexPair> getExtractBetweens() {
        return Collections.unmodifiableList(extractBetweens);
    }
    /**
     * Sets the patterns delimiting the portions of a document to be considered
     * for link extraction.
     * @param betweens extract between patterns
     * @since 2.8.0
     */
    public void setExtractBetweens(RegexPair... betweens) {
        CollectionUtil.setAll(this.extractBetweens, betweens);
    }
    /**
     * Sets the patterns delimiting the portions of a document to be considered
     * for link extraction.
     * @param betweens extract between patterns
     * @since 3.0.0
     */
    public void setExtractBetweens(List<RegexPair> betweens) {
        CollectionUtil.setAll(this.extractBetweens, betweens);
    }
    /**
     * Adds patterns delimiting a portion of a document to be considered
     * for link extraction.
     * @param start pattern matching start of text portion
     * @param end pattern matching end of text portion
     * @param caseSensitive whether the patterns are case sensitive or not
     * @since 2.8.0
     */
    public void addExtractBetween(
            String start, String end, boolean caseSensitive) {
        this.extractBetweens.add(new RegexPair(start, end, caseSensitive));
    }

    /**
     * Gets the patterns delimiting the portions of a document to be excluded
     * from link extraction.
     * @return extract between patterns
     * @since 2.8.0
     */
    public List<RegexPair> getNoExtractBetweens() {
        return Collections.unmodifiableList(noExtractBetweens);
    }
    /**
     * Sets the patterns delimiting the portions of a document to be excluded
     * from link extraction.
     * @param betweens extract between patterns
     * @since 2.8.0
     */
    public void setNoExtractBetweens(RegexPair... betweens) {
        CollectionUtil.setAll(this.noExtractBetweens, betweens);
    }
    /**
     * Sets the patterns delimiting the portions of a document to be excluded
     * from link extraction.
     * @param betweens extract between patterns
     * @since 3.0.0
     */
    public void setNoExtractBetweens(List<RegexPair> betweens) {
        CollectionUtil.setAll(this.noExtractBetweens, betweens);
    }
    /**
     * Adds patterns delimiting a portion of a document to be excluded
     * from link extraction.
     * @param start pattern matching start of text portion
     * @param end pattern matching end of text portion
     * @param caseSensitive whether the patterns are case sensitive or not
     * @since 2.8.0
     */
    public void addNoExtractBetween(
            String start, String end, boolean caseSensitive) {
        this.noExtractBetweens.add(new RegexPair(start, end, caseSensitive));
    }

    /**
     * Gets the selectors matching the portions of a document to be considered
     * for link extraction.
     * @return selectors
     * @since 2.9.0
     */
    public List<String> getExtractSelectors() {
        return Collections.unmodifiableList(extractSelectors);
    }
    /**
     * Sets the selectors matching the portions of a document to be considered
     * for link extraction.
     * @param selectors selectors
     * @since 2.9.0
     */
    public void setExtractSelectors(String... selectors) {
        CollectionUtil.setAll(this.extractSelectors, selectors);
    }
    /**
     * Adds selectors matching the portions of a document to be considered
     * for link extraction.
     * @param selectors selectors
     * @since 2.9.0
     */
    public void addExtractSelectors(String... selectors) {
        this.extractSelectors.addAll(Arrays.asList(selectors));
    }

    /**
     * Gets the selectors matching the portions of a document to be excluded
     * from link extraction.
     * @return selectors
     * @since 2.9.0
     */
    public List<String> getNoExtractSelectors() {
        return Collections.unmodifiableList(noExtractSelectors);
    }
    /**
     * Sets the selectors matching the portions of a document to be excluded
     * from link extraction.
     * @param selectors selectors
     * @since 2.9.0
     */
    public void setNoExtractSelectors(String... selectors) {
        CollectionUtil.setAll(this.noExtractSelectors, selectors);
    }
    /**
     * Adds selectors matching the portions of a document to be excluded
     * from link extraction.
     * @param selectors selectors
     * @since 2.9.0
     */
    public void addNoExtractSelectors(String... selectors) {
        this.noExtractSelectors.addAll(Arrays.asList(selectors));
    }

    /**
     * Gets whether links should be extracted from HTML/XML comments.
     * @return <code>true</code> if links should be extracted from comments.
     * @since 2.6.0
     */
    public boolean isCommentsEnabled() {
        return commentsEnabled;
    }
    /**
     * Sets whether links should be extracted from HTML/XML comments.
     * @param commentsEnabled <code>true</code> if links
     *        should be extracted from comments.
     * @since 2.6.0
     */
    public void setCommentsEnabled(boolean commentsEnabled) {
        this.commentsEnabled = commentsEnabled;
    }

    /**
     * Gets the schemes to be extracted.
     * @return schemes to be extracted
     * @since 2.4.0
     */
    public List<String> getSchemes() {
        return Collections.unmodifiableList(schemes);
    }
    /**
     * Sets the schemes to be extracted.
     * @param schemes schemes to be extracted
     * @since 2.4.0
     */
    public void setSchemes(String... schemes) {
        CollectionUtil.setAll(this.schemes, schemes);
    }
    /**
     * Sets the schemes to be extracted.
     * @param schemes schemes to be extracted
     * @since 3.0.0
     */
    public void setSchemes(List<String> schemes) {
        CollectionUtil.setAll(this.schemes, schemes);
    }

    public boolean isIgnoreNofollow() {
        return ignoreNofollow;
    }
    public void setIgnoreNofollow(boolean ignoreNofollow) {
        this.ignoreNofollow = ignoreNofollow;
    }

    /**
     * Gets the character set of pages on which link extraction is performed.
     * Default is <code>null</code> (charset detection will be attempted).
     * @return character set to use, or <code>null</code>
     * @since 2.4.0
     */
    public String getCharset() {
        return charset;
    }
    /**
     * Sets the character set of pages on which link extraction is performed.
     * Not specifying any (<code>null</code>) will attempt charset detection.
     * @param charset character set to use, or <code>null</code>
     * @since 2.4.0
     */
    public void setCharset(String charset) {
        this.charset = charset;
    }

    public synchronized void addLinkTag(String tagName, String attribute) {
        tagAttribs.add(tagName, attribute);
        resetTagPattern();
    }
    public synchronized void removeLinkTag(String tagName, String attribute) {
        if (attribute == null) {
            tagAttribs.remove(tagName);
        } else {
            List<String> values = tagAttribs.getStrings(tagName);
            values.remove(attribute);
            if (values.isEmpty()) {
                tagAttribs.remove(tagName);
            } else {
                tagAttribs.setList(tagName, values);
            }
        }
        resetTagPattern();
    }
    public synchronized void clearLinkTags() {
        tagAttribs.clear();
        resetTagPattern();
    }

    private void resetTagPattern() {
        String tagNames = StringUtils.join(tagAttribs.keySet(), '|');
        tagPattern = Pattern.compile(
                "<(" + tagNames + ")((\\s*>)|(\\s([^\\<]*?)>))", PATTERN_FLAGS);
    }

    private Pattern getTagBodyPattern(String name) {
        return Pattern.compile(
                "<\\s*" + name + "[^<]*?>([^<]*?)<\\s*/\\s*" + name + "\\s*>",
                PATTERN_FLAGS);
    }

    //--- Extract Links --------------------------------------------------------
    private static final Pattern A_TEXT_PATTERN = Pattern.compile(
            "<a[^<]+?>(.*?)<\\s*/\\s*a\\s*>", PATTERN_FLAGS);
    private static final Pattern A_TITLE_PATTERN = Pattern.compile(
            "\\s*title\\s*=\\s*([\"'])(.*?)\\1", PATTERN_FLAGS);
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
            "(<\\s*script\\b.*?>)(.*?)(<\\s*/\\s*script\\s*>)", PATTERN_FLAGS);
    private static final Pattern COMMENT_PATTERN = Pattern.compile(
            "<!--.*?-->", PATTERN_FLAGS);
    private void extractLinks(
            String theContent, Referer referrer, Set<Link> links) {
        String content = theContent;

        // Eliminate content not matching extract patterns
        content = excludeUnwantedContent(content);

        // Get rid of <script> tags content to eliminate possibly
        // generated URLs.
        content = SCRIPT_PATTERN.matcher(content).replaceAll("$1$3");
        if (!isCommentsEnabled()) {
            content = COMMENT_PATTERN.matcher(content).replaceAll("");
        }

        Matcher matcher = tagPattern.matcher(content);
        while (matcher.find()) {
            String tagName = matcher.group(1);
            String restOfTag = matcher.group(4);
            String attribs = tagAttribs.getString(tagName);

            //--- the body value of the tag is taken as URL ---
            if (StringUtils.isBlank(attribs)) {
                Pattern bodyPattern = getTagBodyPattern(tagName);
                Matcher bodyMatcher = bodyPattern.matcher(content);
                String url = null;
                if (bodyMatcher.find(matcher.start())) {
                    url = bodyMatcher.group(1).trim();
                    url = toCleanAbsoluteURL(referrer, url);
                    if (url == null) {
                        continue;
                    }
                    Link link = new Link(url);
                    link.setReferrer(referrer.url);
                    link.setTag(tagName);
                    links.add(link);
                }
                continue;
            }

            //--- a tag attribute has the URL ---
            String text = null;
            String title = null;
            if (StringUtils.isBlank(restOfTag)) {
                continue;
            }
            if ("meta".equalsIgnoreCase(tagName)) {
                extractMetaRefresh(restOfTag, referrer, links);
                continue;
            }
            if ("a".equalsIgnoreCase(tagName)) {
                if (!ignoreNofollow && isNofollow(restOfTag)) {
                    continue;
                }
                Matcher textMatcher = A_TEXT_PATTERN.matcher(content);
                if (textMatcher.find(matcher.start())) {
                    text = textMatcher.group(1).trim();
                    // Strip markup to only extract the text
                    text = text.replaceAll("<[^>]*>", "");
                }
                Matcher titleMatcher = A_TITLE_PATTERN.matcher(restOfTag);
                if (titleMatcher.find()) {
                    title = titleMatcher.group(2).trim();
                }
            }

            Pattern p = Pattern.compile(
                    "(^|\\s)(" + attribs + ")\\s*=\\s*"
                  + "((?<quot>[\"'])(?<url1>[^\\<\\>]*?)\\k<quot>"
                  + "|(?<url2>[^\\s\\>]+)[\\s\\>])", PATTERN_FLAGS);

            Matcher urlm = p.matcher(restOfTag);
            while (urlm.find()) {
                String attribName = urlm.group(2);
                // Will either match url1 (quoted) or url2 (unquoted).
                String matchedUrl = urlm.start("url1") != -1
                        ? urlm.group("url1") : urlm.group("url2");
                if (StringUtils.isBlank(matchedUrl)) {
                    continue;
                }
                String[] urls = null;
                if ("object".equalsIgnoreCase(tagName)) {
                    urls = StringUtils.split(matchedUrl, ' ');
                } else if ("applet".equalsIgnoreCase(tagName)) {
                    urls = StringUtils.split(matchedUrl, ", ");
                } else {
                    urls = new String[] { matchedUrl };
                }

                for (String url : urls) {
                    url = toCleanAbsoluteURL(referrer, url);
                    if (url == null) {
                        continue;
                    }
                    Link link = new Link(url);
                    link.setReferrer(referrer.url);
                    link.setTag(tagName + "." + attribName);
                    if (StringUtils.isNotBlank(text)) {
                        link.setText(text);
                    }
                    if (StringUtils.isNotBlank(title)) {
                        link.setTitle(title);
                    }
                    links.add(link);
                }
            }
        }
    }

    //TODO consider moving this logic to new class shared with others,
    //like StripBetweenTagger
    private String excludeUnwantedContent(String content) {
        String newContent = content;
        if (!extractBetweens.isEmpty()) {
            newContent = applyExtractBetweens(newContent);
        }
        if (!noExtractBetweens.isEmpty()) {
            newContent = applyNoExtractBetweens(newContent);
        }
        if (!extractSelectors.isEmpty()) {
            newContent = applyExtractSelectors(newContent);
        }
        if (!noExtractSelectors.isEmpty()) {
            newContent = applyNoExtractSelectors(newContent);
        }
        return newContent;
    }

    private String applyExtractBetweens(String content) {
        StringBuilder b = new StringBuilder();
        for (RegexPair regexPair : extractBetweens) {
            for (Pair<Integer, Integer> pair :
                    matchBetweens(content, regexPair)) {
                b.append(content.substring(pair.getLeft(), pair.getRight()));
            }
        }
        return b.toString();
    }
    private String applyNoExtractBetweens(String content) {
        StringBuilder b = new StringBuilder(content);
        for (RegexPair regexPair : noExtractBetweens) {
            List<Pair<Integer, Integer>> matches =
                    matchBetweens(content, regexPair);
            for (int i = matches.size() -1; i >= 0; i--) {
                Pair<Integer, Integer> pair = matches.get(i);
                b.delete(pair.getLeft(), pair.getRight());
            }
        }
        return b.toString();
    }
    private List<Pair<Integer, Integer>> matchBetweens(
            String content, RegexPair pair) {
        int flags = Pattern.DOTALL;
        if (!pair.isCaseSensitive()) {
            flags = flags | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        List<Pair<Integer, Integer>> matches = new ArrayList<>();
        Pattern leftPattern = Pattern.compile(pair.getStart(), flags);
        Matcher leftMatch = leftPattern.matcher(content);
        while (leftMatch.find()) {
            Pattern rightPattern = Pattern.compile(pair.getEnd(), flags);
            Matcher rightMatch = rightPattern.matcher(content);
            if (rightMatch.find(leftMatch.end())) {
                matches.add(new ImmutablePair<>(
                        leftMatch.start(), rightMatch.end()));
            } else {
                break;
            }
        }
        return matches;
    }

    private String applyExtractSelectors(String content) {
        StringBuilder b = new StringBuilder();
        Document doc = Jsoup.parse(content);
        for (String selector : extractSelectors) {
            for (Element element : doc.select(selector)) {
                if (b.length() > 0) {
                    b.append(" ");
                }
                b.append(element.html());
            }
        }
        return b.toString();
    }
    private String applyNoExtractSelectors(String content) {
        Document doc = Jsoup.parse(content);
        for (String selector : noExtractSelectors) {
            for (Element element : doc.select(selector)) {
                element.remove();
            }
        }
        return doc.toString();
    }
    //--- Extract meta refresh -------------------------------------------------
    private static final Pattern META_EQUIV_REFRESH_PATTERN = Pattern.compile(
            "(^|\\W+)http-equiv\\s*=\\s*[\"']{0,1}refresh[\"']{0,1}",
            PATTERN_FLAGS);
    private static final Pattern META_CONTENT_URL_PATTERN = Pattern.compile(
            "(^|\\W+)content\\s*=\\s*([\"'])[^a-zA-Z]*url"
          + "\\s*=\\s*([\"']{0,1})([^\\<\\>\"']+?)[\\<\\>\"'].*?",
            PATTERN_FLAGS);
    private void extractMetaRefresh(
            String restOfTag, Referer referrer, Set<Link> links) {
        if (!META_EQUIV_REFRESH_PATTERN.matcher(restOfTag).find()) {
            return;
        }
        Matcher m = META_CONTENT_URL_PATTERN.matcher(restOfTag);
        if (!m.find()) {
            return;
        }
        String url = toCleanAbsoluteURL(referrer, m.group(4));
        Link link = new Link(url);
        link.setReferrer(referrer.url);
        link.setTag("meta.http-equiv.refresh");
        links.add(link);
    }

    //--- Has a nofollow attribute? --------------------------------------------
    private static final Pattern NOFOLLOW_PATTERN = Pattern.compile(
            "(^|\\s)rel\\s*=\\s*([\"']{0,1})(\\s*nofollow\\s*)\\2",
            PATTERN_FLAGS);
    private boolean isNofollow(String attribs) {
        if (StringUtils.isBlank(attribs)) {
            return false;
        }
        return NOFOLLOW_PATTERN.matcher(attribs).find();
    }


    private String toCleanAbsoluteURL(
            final Referer urlParts, final String newURL) {
        String url = StringUtils.trimToNull(newURL);
        if (!isValidNewURL(url)) {
            return null;
        }

        // Decode HTML entities.
        url = StringEscapeUtils.unescapeHtml4(url);

        // Revalidate after unescaping
        if (!isValidNewURL(url)) {
            return null;
        }

        if (url.startsWith("//")) {
            // this is URL relative to protocol
            url = urlParts.scheme
                    + StringUtils.substringAfter(url, "//");
        } else if (url.startsWith("/")) {
            // this is a URL relative to domain name
            url = urlParts.absoluteBase + url;
        } else if (url.startsWith("?") || url.startsWith("#")) {
            // this is a relative url and should have the full page base
            url = urlParts.documentBase + url;
        } else if (!url.contains(":")) {
            if (urlParts.relativeBase.endsWith("/")) {
                // This is a URL relative to the last URL segment
                url = urlParts.relativeBase + url;
            } else {
                url = urlParts.relativeBase + "/" + url;
            }
        }

        if (url.length() > maxURLLength) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("URL length ({}) exceeding maximum length allowed "
                        + "({}) to be extracted. URL (showing first {} chars): "
                        + "{}...",
                        url.length(), maxURLLength, LOGGING_MAX_URL_LENGTH,
                        StringUtils.substring(url, 0, LOGGING_MAX_URL_LENGTH));
            }
            return null;
        }

        return url;
    }

    private static final Pattern SCHEME_PATTERN = Pattern.compile(
            "^[a-z][a-z0-9\\+\\.\\-]*:.*$", Pattern.CASE_INSENSITIVE);
    private boolean isValidNewURL(String newURL) {
        if (StringUtils.isBlank(newURL)) {
            return false;
        }

        // if scheme is specified, make sure it is valid
        if (SCHEME_PATTERN.matcher(newURL).matches()) {
            List<String> supportedSchemes = getSchemes();
            if (supportedSchemes.isEmpty()) {
                supportedSchemes = DEFAULT_SCHEMES;
            }
            for (String scheme : supportedSchemes) {
                if (StringUtils.startsWithIgnoreCase(newURL, scheme + ":")) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Override
    protected void loadLinkExtractorFromXML(XML xml) {
        setMaxURLLength(xml.getInteger("@maxURLLength", maxURLLength));
        setIgnoreNofollow(xml.getBoolean("@ignoreNofollow", ignoreNofollow));
        setCommentsEnabled(xml.getBoolean("@commentsEnabled", commentsEnabled));
        setCharset(xml.getString("@charset", charset));
        setSchemes(xml.getDelimitedStringList("schemes", schemes));

        // tag & attributes
        List<XML> xmlTags = xml.getXMLList("tags/tag");
        if (!xmlTags.isEmpty()) {
            clearLinkTags();
            for (XML xmlTag: xmlTags) {
                String name = xmlTag.getString("@name", null);
                String attr = xmlTag.getString("@attribute", null);
                if (StringUtils.isNotBlank(name)) {
                    addLinkTag(name, attr);
                }
            }
        }

        // extract between
        List<XML> xmlBetweens = xml.getXMLList("extractBetween");
        if (!xmlBetweens.isEmpty()) {
            extractBetweens.clear();
            for (XML xmlBetween: xmlBetweens) {
                addExtractBetween(
                        xmlBetween.getString("start", null),
                        xmlBetween.getString("end", null),
                        xmlBetween.getBoolean("@caseSensitive", false));
            }
        }

        // no extract between
        List<XML> xmlNoBetweens = xml.getXMLList("noExtractBetween");
        if (!xmlNoBetweens.isEmpty()) {
            noExtractBetweens.clear();
            for (XML xmlNoBetween: xmlNoBetweens) {
                addNoExtractBetween(
                        xmlNoBetween.getString("start", null),
                        xmlNoBetween.getString("end", null),
                        xmlNoBetween.getBoolean("@caseSensitive", false));
            }
        }

        // extract selector
        List<String> extractSelList = xml.getStringList("extractSelector");
        if (!extractSelList.isEmpty()) {
            CollectionUtil.setAll(this.extractSelectors, extractSelList);
        }

        // no extract selector
        List<String> noExtractSelList = xml.getStringList("noExtractSelector");
        if (!noExtractSelList.isEmpty()) {
            CollectionUtil.setAll(this.noExtractSelectors, noExtractSelList);
        }

    }
    @Override
    protected void saveLinkExtractorToXML(XML xml) {
        xml.setAttribute("maxURLLength", maxURLLength);
        xml.setAttribute("ignoreNofollow", ignoreNofollow);
        xml.setAttribute("commentsEnabled", commentsEnabled);
        xml.setAttribute("charset", charset);
        xml.addDelimitedElementList("schemes", schemes);

        // Tags
        XML xmlTags = xml.addElement("tags");
        for (Entry<String, List<String>> entry :
                tagAttribs.entrySet()) {
            for (String attrib : entry.getValue()) {
                xmlTags.addElement("tag")
                        .setAttribute("name", entry.getKey())
                        .setAttribute("attribute", attrib);
            }
        }

        // extract between
        for (RegexPair pair : extractBetweens) {
            XML xmlBetween = xml.addElement("extractBetween")
                    .setAttribute("caseSensitive", pair.caseSensitive);
            xmlBetween.addElement("start", pair.getStart());
            xmlBetween.addElement("end", pair.getEnd());
        }

        // no extract between
        for (RegexPair pair : noExtractBetweens) {
            XML xmlNoBetween = xml.addElement("noExtractBetween")
                    .setAttribute("caseSensitive", pair.caseSensitive);
            xmlNoBetween.addElement("start", pair.getStart());
            xmlNoBetween.addElement("end", pair.getEnd());
        }

        // extract selector
        xml.addElementList("extractSelector", extractSelectors);

        // no extract selector
        xml.addElementList("noExtractSelector", noExtractSelectors);

    }

    //TODO delete this class and use HttpURL#toAbsolute() instead?
    private static class Referer {
        private final String scheme;
        private final String path;
        private final String relativeBase;
        private final String absoluteBase;
        private final String documentBase;
        private final String url;
        public Referer(String documentUrl) {
            super();
            this.url = documentUrl;

            // URL Protocol/scheme, up to double slash (included)
            scheme = documentUrl.replaceFirst("(.*?:(//){0,1})(.*)", "$1");

            // URL Path (anything after double slash)
            path = documentUrl.replaceFirst("(.*?:(//){0,1})(.*)", "$3");

            // URL Relative Base: truncate to last / before a ? or #
            String relBase = path.replaceFirst(
                    "(.*?)([\\?\\#])(.*)", "$1");
            relativeBase = scheme +  relBase.replaceFirst("(.*/)(.*)", "$1");

            // URL Absolute Base: truncate to first / if present, after protocol
            absoluteBase = scheme + path.replaceFirst("(.*?)(/.*)", "$1");

            // URL Document Base: truncate from first ? or #
            documentBase =
                    scheme + path.replaceFirst("(.*?)([\\?\\#])(.*)", "$1");

            LOG.trace("DOCUMENT URL: {}  BASE RELATIVE: {}  BASE ABSOLUTE: {}",
                    documentUrl, relativeBase, absoluteBase);
        }
    }

    //TODO make standalone class?
    public static class RegexPair {
        private final String start;
        private final String end;
        private final boolean caseSensitive;
        public RegexPair(String start, String end, boolean caseSensitive) {
            super();
            this.start = start;
            this.end = end;
            this.caseSensitive = caseSensitive;
        }
        public String getStart() {
            return start;
        }
        public String getEnd() {
            return end;
        }
        public boolean isCaseSensitive() {
            return caseSensitive;
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
            return ReflectionToStringBuilder.toString(
                    this, ToStringStyle.SHORT_PREFIX_STYLE);
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
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
