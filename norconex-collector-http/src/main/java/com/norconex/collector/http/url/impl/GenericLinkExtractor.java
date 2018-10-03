/* Copyright 2014-2018 Norconex Inc.
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
package com.norconex.collector.http.url.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.tika.utils.CharsetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.collector.http.url.Link;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.util.CharsetUtil;

/**
 * Generic link extractor for URLs found in HTML and possibly other text files.
 *
 * <h3>Content-types</h3>
 * By default, this extractor will look for URLs only in documents matching
 * one of these content types:
 * <pre>
 * text/html, application/xhtml+xml, vnd.wap.xhtml+xml, x-asp
 * </pre>
 * You can specify your own content types if you know they represent a file
 * with HTML-like markup tags containing URLs.  For documents that are just
 * too different, consider implementing your own {@link ILinkExtractor} instead.
 * Removing the default values and define no content types will have for effect
 * to try to extract URLs from all files (usually a bad idea).
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
 *   {@link HttpMetadata#COLLECTOR_REFERRER_REFERENCE}.</li>
 *   <li><b>Referrer link tag:</b> The tag and attribute names of the link
 *   that contained the document reference (URL) in referrer's content.
 *   Metadata value is
 *   {@link HttpMetadata#COLLECTOR_REFERRER_LINK_TAG}.</li>
 *   <li><b>Referrer link text:</b> The text between the
 *   <code>&lt;a href=""&gt;&lt;/a&gt;</code> tags of the referrer document.
 *   Can be useful to help establish better document titles.
 *   Metadata value is
 *   {@link HttpMetadata#COLLECTOR_REFERRER_LINK_TEXT}.</li>
 *   <li><b>Referrer link title:</b> The <code>title</code> attribute of the
     link that contained the document reference (URL) in referrer's content.
 *   Can also be useful to help establish better document titles.
 *   Metadata value is
 *   {@link HttpMetadata#COLLECTOR_REFERRER_LINK_TITLE}.</li>
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
 *
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;extractor class="com.norconex.collector.http.url.impl.GenericLinkExtractor"
 *          maxURLLength="(maximum URL length. Default is 2048)"
 *          ignoreNofollow="[false|true]"
 *          commentsEnabled="[false|true]"
 *          charset="(supported character encoding)" &gt;
 *      &lt;contentTypes&gt;
 *          (CSV list of content types on which to perform link extraction.
 *           leave blank or remove tag to use defaults.)
 *      &lt;/contentTypes&gt;
 *      &lt;schemes&gt;
 *          (CSV list of URI scheme for which to perform link extraction.
 *           leave blank or remove tag to use defaults.)
 *      &lt;/schemes&gt;
 *
 *      &lt;!-- Which tags and attributes hold the URLs to extract. --&gt;
 *      &lt;tags&gt;
 *          &lt;tag name="(tag name)" attribute="(tag attribute)" /&gt;
 *          &lt;!-- you can have multiple tag entries --&gt;
 *      &lt;/tags&gt;
 *
 *      &lt;!-- Only extract URLs from the following text portions. --&gt;
 *      &lt;extractBetween caseSensitive="[false|true]"&gt;
 *          &lt;start&gt;(regex)&lt;/start&gt;
 *          &lt;end&gt;(regex)&lt;/end&gt;
 *      &lt;/extractBetween&gt;
 *      &lt;!-- you can have multiple extractBetween entries --&gt;
 *
 *      &lt;!-- Do not extract URLs from the following text portions. --&gt;
 *      &lt;noExtractBetween caseSensitive="[false|true]"&gt;
 *          &lt;start&gt;(regex)&lt;/start&gt;
 *          &lt;end&gt;(regex)&lt;/end&gt;
 *      &lt;/noExtractBetween&gt;
 *      &lt;!-- you can have multiple noExtractBetween entries --&gt;
 *
 *  &lt;/extractor&gt;
 * </pre>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following adds URLs to JavaScript files to the list of URLs to be
 * extracted.
 * </p>
 * <pre>
 *  &lt;extractor class="com.norconex.collector.http.url.impl.GenericLinkExtractor"&gt;
 *      &lt;tags&gt;
 *          &lt;tag name="a" attribute="href" /&gt;
 *          &lt;tag name="frame" attribute="src" /&gt;
 *          &lt;tag name="iframe" attribute="src" /&gt;
 *          &lt;tag name="img" attribute="src" /&gt;
 *          &lt;tag name="meta" attribute="http-equiv" /&gt;
 *          &lt;tag name="script" attribute="src" /&gt;
 *      &lt;/tags&gt;
 *  &lt;/extractor&gt;
 * </pre>
 *
 * @author Pascal Essiembre
 * @since 2.3.0
 */
public class GenericLinkExtractor implements ILinkExtractor, IXMLConfigurable {


    private static final Logger LOG = LoggerFactory.getLogger(
            GenericLinkExtractor.class);

    //TODO make buffer size and overlap size configurable
    //1MB: make configurable
    public static final int MAX_BUFFER_SIZE = 1024 * 1024;
    // max url leng is 2048 x 2 bytes x 2 for <a> anchor attributes.
    public static final int OVERLAP_SIZE = 2 * 2 * 2048;

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;

    private static final List<ContentType> DEFAULT_CONTENT_TYPES =
            Collections.unmodifiableList(Arrays.asList(
                ContentType.HTML,
                ContentType.valueOf("application/xhtml+xml"),
                ContentType.valueOf("vnd.wap.xhtml+xml"),
                ContentType.valueOf("x-asp")
            ));

    private static final List<String> DEFAULT_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("http", "https", "ftp"));

    private static final int PATTERN_FLAGS =
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final int LOGGING_MAX_URL_LENGTH = 200;

    private final List<ContentType> contentTypes =
            new ArrayList<>(DEFAULT_CONTENT_TYPES);
    private final List<String> schemes = new ArrayList<>(DEFAULT_SCHEMES);
    private int maxURLLength = DEFAULT_MAX_URL_LENGTH;
    private boolean ignoreNofollow;
    private final Properties tagAttribs = new Properties(true);
    transient private Pattern tagPattern;
    transient private String charset;
    transient private boolean commentsEnabled;

    transient private final List<RegexPair> extractBetweens = new ArrayList<>();
    transient private final List<RegexPair> noExtractBetweens = new ArrayList<>();

    public GenericLinkExtractor() {
        super();
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
    public Set<Link> extractLinks(InputStream input, String reference,
            ContentType contentType) throws IOException {

        List<ContentType> cTypes = contentTypes;
        if (cTypes.isEmpty()) {
            cTypes = DEFAULT_CONTENT_TYPES;
        }

        // Do not extract if not a supported content type
        if (!cTypes.contains(contentType)) {
            return Collections.emptySet();
        }

        // Do it, extract Links
        String sourceCharset = getCharset();
        if (StringUtils.isBlank(sourceCharset)) {
            sourceCharset = CharsetUtil.detectCharset(input);
        } else {
            sourceCharset = CharsetUtils.clean(sourceCharset);
        }
        sourceCharset = StringUtils.defaultIfBlank(
                sourceCharset, StandardCharsets.UTF_8.toString());

        Referer referer = new Referer(reference);
        Set<Link> links = new HashSet<>();

        StringBuilder sb = new StringBuilder();
        Reader r =
                new BufferedReader(new InputStreamReader(input, sourceCharset));
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
        return links;
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


    @Override
    public boolean accepts(String url, ContentType contentType) {
        if (contentTypes.isEmpty()) {
            return true;
        }
        return contentTypes.contains(contentType);
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

    public List<ContentType> getContentTypes() {
        return Collections.unmodifiableList(contentTypes);
    }
    /**
     * Sets the content types on which to perform link extraction.
     * @param contentTypes content types
     */
    public void setContentTypes(ContentType... contentTypes) {
        CollectionUtil.setAll(this.contentTypes, contentTypes);
    }
    /**
     * Sets the content types on which to perform link extraction.
     * @param contentTypes content types
     * @since 3.0.0
     */
    public void setContentTypes(List<ContentType> contentTypes) {
        CollectionUtil.setAll(this.contentTypes, contentTypes);
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
                tagAttribs.set(tagName, values);
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
            newContent = excludeUnwantedContent(newContent, true);
        }
        if (!noExtractBetweens.isEmpty()) {
            newContent = excludeUnwantedContent(newContent, false);
        }
        return newContent;
    }
    private String excludeUnwantedContent(String content, boolean keepMatch) {
        StringBuilder newContent = new StringBuilder();
        if (!keepMatch) {
            newContent.append(content);
        }
        List<RegexPair> pairs;
        if (keepMatch) {
            pairs = extractBetweens;
        } else {
            pairs = noExtractBetweens;
        }
        for (RegexPair pair : pairs) {
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
            excludeUnwantedContent(newContent, content, matches, keepMatch);
        }
        return newContent.toString();
    }
    private void excludeUnwantedContent(
            StringBuilder newContent, String content,
            List<Pair<Integer, Integer>> matches, boolean keepMatch) {
        if (keepMatch) {
            for (Pair<Integer, Integer> pair : matches) {
                newContent.append(
                        content.substring(pair.getLeft(), pair.getRight()));
            }
        } else {
            for (int i = matches.size() -1; i >= 0; i--) {
                Pair<Integer, Integer> pair = matches.get(i);
                newContent.delete(pair.getLeft(), pair.getRight());
            }
        }
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
    public void loadFromXML(XML xml) {
        setMaxURLLength(xml.getInteger("@maxURLLength", maxURLLength));
        setIgnoreNofollow(xml.getBoolean("@ignoreNofollow", ignoreNofollow));
        setCommentsEnabled(xml.getBoolean("@commentsEnabled", commentsEnabled));
        setCharset(xml.getString("@charset", charset));
        setContentTypes(xml.getDelimitedList(
                "contentTypes", ContentType.class, contentTypes));
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
//        List<HierarchicalConfiguration> tagNodes =
//                xml.configurationsAt("tags.tag");
//        if (!tagNodes.isEmpty()) {
//            clearLinkTags();
//            for (HierarchicalConfiguration tagNode : tagNodes) {
//                String name = tagNode.getString("@name", null);
//                String attr = tagNode.getString("@attribute", null);
//                if (StringUtils.isNotBlank(name)) {
//                    addLinkTag(name, attr);
//                }
//            }
//        }

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
//        List<HierarchicalConfiguration> extractNodes =
//                xml.configurationsAt("extractBetween");
//        if (!extractNodes.isEmpty()) {
//            extractBetweens.clear();
//            for (HierarchicalConfiguration node : extractNodes) {
//                addExtractBetween(
//                        node.getString("start", null),
//                        node.getString("end", null),
//                        node.getBoolean("@caseSensitive", false));
//            }
//        }

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
//        List<HierarchicalConfiguration> noExtractNodes =
//                xml.configurationsAt("noExtractBetween");
//        if (!noExtractNodes.isEmpty()) {
//            noExtractBetweens.clear();
//            for (HierarchicalConfiguration node : noExtractNodes) {
//                addNoExtractBetween(
//                        node.getString("start", null),
//                        node.getString("end", null),
//                        node.getBoolean("@caseSensitive", false));
//            }
//        }
    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("maxURLLength", maxURLLength);
        xml.setAttribute("ignoreNofollow", ignoreNofollow);
        xml.setAttribute("commentsEnabled", commentsEnabled);
        xml.setAttribute("charset", charset);
        xml.addDelimitedElementList("contentTypes", contentTypes);
        xml.addDelimitedElementList("schemes", schemes);

        // Content Types
//        if (!ArrayUtils.isEmpty(getContentTypes())) {
//            xml.addElement("contentTypes",
//                    StringUtils.join(getContentTypes(), ','));
//        }

        // Schemes
//        if (!ArrayUtils.isEmpty(getSchemes())) {
//            xml.addElement("schemes",
//                    StringUtils.join(getSchemes(), ','));
//        }

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

//        writer.writeStartElement("tags");
//        for (Map.Entry<String, List<String>> entry :
//                tagAttribs.entrySet()) {
//            for (String attrib : entry.getValue()) {
//                writer.writeStartElement("tag");
//                writer.writeAttributeString("name", entry.getKey());
//                writer.writeAttributeString("attribute", attrib);
//                writer.writeEndElement();
//            }
//        }
//        writer.writeEndElement();

        // extract between
        for (RegexPair pair : extractBetweens) {
            XML xmlBetween = xml.addElement("extractBetween")
                    .setAttribute("caseSensitive", pair.caseSensitive);
            xmlBetween.addElement("start", pair.getStart());
            xmlBetween.addElement("end", pair.getEnd());
        }
//        for (RegexPair pair : extractBetweens) {
//            writer.writeStartElement("extractBetween");
//            writer.writeAttributeBoolean(
//                    "caseSensitive", pair.isCaseSensitive());
//            xml.addElement("start", pair.getStart());
//            xml.addElement("end", pair.getEnd());
//            writer.writeEndElement();
//        }

        // no extract between
        for (RegexPair pair : noExtractBetweens) {
            XML xmlNoBetween = xml.addElement("noExtractBetween")
                    .setAttribute("caseSensitive", pair.caseSensitive);
            xmlNoBetween.addElement("start", pair.getStart());
            xmlNoBetween.addElement("end", pair.getEnd());
        }
//        for (RegexPair pair : noExtractBetweens) {
//            writer.writeStartElement("noExtractBetween");
//            writer.writeAttributeBoolean(
//                    "caseSensitive", pair.isCaseSensitive());
//            xml.addElement("start", pair.getStart());
//            xml.addElement("end", pair.getEnd());
//            writer.writeEndElement();
//        }
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

            LOG.trace("DOCUMENT URL ----> {}", documentUrl);
            LOG.trace("  BASE RELATIVE -> {}", relativeBase);
            LOG.trace("  BASE ABSOLUTE -> {}", absoluteBase);
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
            return EqualsBuilder.reflectionEquals(this, other, false);
        }
        @Override
        public int hashCode() {
            return HashCodeBuilder.reflectionHashCode(this, false);
        }
        @Override
        public String toString() {
            return ReflectionToStringBuilder.toString(
                    this, ToStringStyle.SHORT_PREFIX_STYLE);
        }
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other, "tagPattern");
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, "tagPattern");
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE)
                .setExcludeFieldNames("tagPattern").toString();
    }
}
