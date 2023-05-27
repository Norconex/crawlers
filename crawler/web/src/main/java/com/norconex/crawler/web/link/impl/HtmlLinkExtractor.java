/* Copyright 2014-2023 Norconex Inc.
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

import static com.norconex.commons.lang.EqualsUtil.equalsAny;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import com.google.common.base.Objects;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.link.AbstractTextLinkExtractor;
import com.norconex.crawler.web.link.Link;
import com.norconex.crawler.web.link.LinkExtractor;
import com.norconex.crawler.web.url.WebURLNormalizer;
import com.norconex.crawler.web.url.impl.GenericURLNormalizer;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;
import com.norconex.importer.handler.HandlerDoc;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * A memory efficient HTML link extractor.
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
 * of {@link WebURLNormalizer} to strip it if need be.
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
 * {@link #setExtractBetweens(RegexPair...)} and
 * {@link #setNoExtractBetweens(RegexPair...)}. Eligible content for link
 * extraction is identified first, and content to exclude is done on that
 * subset.
 * </p>
 * <p>You can further limit link extraction to specific
 * area by using
 * <a href="https://jsoup.org/cookbook/extracting-data/selector-syntax">selector-syntax</a>
 * to do so, with
 * {@link #setExtractSelectors(String...)} and
 * {@link #setNoExtractSelectors(String...)}.
 * </p>
 *
 * {@nx.xml.usage
 * <extractor class="com.norconex.crawler.web.link.impl.HtmlLinkExtractor"
 *     maxURLLength="(maximum URL length. Default is 2048)"
 *     ignoreNofollow="[false|true]"
 *     ignoreLinkData="[false|true]"
 *     commentsEnabled="[false|true]"
 *     charset="(supported character encoding)">
 *
 *   {@nx.include com.norconex.crawler.web.link.AbstractTextLinkExtractor@nx.xml.usage}
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
 * <extractor class="com.norconex.crawler.web.link.impl.HtmlLinkExtractor">
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
@Slf4j
@Data
public class HtmlLinkExtractor extends AbstractTextLinkExtractor {

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;

    /** Default supported URL schemes (http, https, and ftp). */
    public static final List<String> DEFAULT_SCHEMES =
            List.of("http", "https", "ftp");

    private static final int LOGGING_MAX_URL_LENGTH = 200;
    private static final String HTTP_EQUIV = "http-equiv";

    //--- Properties -----------------------------------------------------------

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
    private String charset;

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

    // NOTE: When this predicate is invoked the tag name is always lower case
    // and known to have been identified as a target tag name in configuration.
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final BiPredicate<Tag, Set<Link>> tagLinksExtractor =

        //--- From tag body ---
        // When no attributes configured for a tag name, we take the body
        // value as the URL.
        ((BiPredicate<Tag, Set<Link>>) (tag, links) -> Optional.of(tag)
            .filter(t -> t.configAttribNames.isEmpty())
            .filter(t -> isNotBlank(t.body))
            .map(t -> toCleanAbsoluteURL(t.referrer, tag.body.trim()))
            .map(url -> links.add(addMetadataToLink(new Link(url), tag, null)))
            .orElse(false))

        //--- From meta http-equiv tag ---
        // E.g.: <meta http-equiv="refresh" content="...">:
        .or((tag, links) -> Optional.of(tag)
            .filter(t -> "meta".equals(t.name))
            .filter(t -> t.configAttribNames.contains(HTTP_EQUIV))
            .filter(t -> t.attribs.getStrings(HTTP_EQUIV).contains("refresh"))
            .filter(t -> t.attribs.containsKey("content"))
            // very unlikely that we have more than one redirect directives,
            // but loop just in case
            .map(t -> t.attribs.getStrings("content")
                .stream()
                .map(attr -> attr.trim().replaceFirst("^\\d+;", ""))
                .map(url -> url.trim().replaceFirst("^URL\\s*=(.*)$", "$1"))
                .map(url -> StringUtils.strip(url, "\"'"))
                .map(url -> toCleanAbsoluteURL(tag.referrer, url))
                .findFirst()
                .map(url -> links.add(
                        addMetadataToLink(new Link(url), tag, "http.equiv")))
                .orElse(false)
            )
            .get())

        //--- From anchor tag ---
        // E.g.: <a href="...">...</a>
        .or((tag, links) -> Optional.of(tag)
            .filter(t -> "a".equals(t.name))
            .filter(t -> t.configAttribNames.contains("href"))
            .filter(t -> t.attribs.containsKey("href"))
            .filter(t -> ignoreNofollow
                    || !tag.attribs.getStrings("rel").contains("nofollow"))
            .map(t -> toCleanAbsoluteURL(
                    t.referrer, t.attribs.getString("href")))
            .map(url -> links.add(
                    addMetadataToLink(new Link(url), tag, "href")))
            .orElse(false))

        //--- From other matching attributes for tag ---
        .or((tag, links) -> tag.configAttribNames.stream()
            .map(cfgAttr -> Optional.ofNullable(tag.attribs.getString(cfgAttr))
                .map(urlStr -> (equalsAny(tag.name, "object", "applet")
                        ? List.of(StringUtils.split(urlStr, ", "))
                        : List.of(urlStr))
                    .stream()
                    .map(url -> toCleanAbsoluteURL(tag.referrer, url))
                    .map(url -> links.add(
                            addMetadataToLink(new Link(url), tag, cfgAttr)))
                    .anyMatch(Boolean::valueOf)
                )
            )
            .flatMap(Optional::stream)
            .anyMatch(Boolean::valueOf)
        );

    //--- Constructor ----------------------------------------------------------

    public HtmlLinkExtractor() {
        // default content type this extractor applies to
        setRestrictions(CommonRestrictions.htmlContentTypes(
                DocMetadata.CONTENT_TYPE));

        // default tags/attributes used to extract data.
        addLinkTag("a", "href");
        addLinkTag("frame", "src");
        addLinkTag("iframe", "src");
        addLinkTag("img", "src");
        addLinkTag("meta", HTTP_EQUIV);
    }

    //--- Accessors ------------------------------------------------------------

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
    public void setExtractBetweens(List<RegexPair> betweens) {
        CollectionUtil.setAll(extractBetweens, betweens);
    }
    /**
     * Adds patterns delimiting a portion of a document to be considered
     * for link extraction.
     * @param start pattern matching start of text portion
     * @param end pattern matching end of text portion
     * @param caseSensitive whether the patterns are case sensitive or not
     */
    public void addExtractBetween(
            String start, String end, boolean caseSensitive) {
        extractBetweens.add(new RegexPair(start, end, caseSensitive));
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
    public void setNoExtractBetweens(List<RegexPair> betweens) {
        CollectionUtil.setAll(noExtractBetweens, betweens);
    }
    /**
     * Adds patterns delimiting a portion of a document to be excluded
     * from link extraction.
     * @param start pattern matching start of text portion
     * @param end pattern matching end of text portion
     * @param caseSensitive whether the patterns are case sensitive or not
     */
    public void addNoExtractBetween(
            String start, String end, boolean caseSensitive) {
        noExtractBetweens.add(new RegexPair(start, end, caseSensitive));
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
    public void setExtractSelectors(List<String> selectors) {
        CollectionUtil.setAll(extractSelectors, selectors);
    }
    /**
     * Adds selectors matching the portions of a document to be considered
     * for link extraction.
     * @param selectors selectors
     */
    public void addExtractSelectors(List<String> selectors) {
        extractSelectors.addAll(selectors);
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
    public void setNoExtractSelectors(List<String> selectors) {
        CollectionUtil.setAll(noExtractSelectors, selectors);
    }
    /**
     * Adds selectors matching the portions of a document to be excluded
     * from link extraction.
     * @param selectors selectors
     */
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

    //--- Public methods -------------------------------------------------------

    public synchronized void addLinkTag(String tagName, String attribute) {
        tagAttribs.add(tagName, attribute);
    }
    public synchronized void removeLinkTag(String tagName, String attribute) {
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
    }
    public synchronized void clearLinkTags() {
        tagAttribs.clear();
    }

    @Override
    public void extractTextLinks(
            Set<Link> links, HandlerDoc doc, Reader reader) throws IOException {
        var refererUrl = doc.getReference();
        try (var r = new TextReader(reader)) {
            var firstChunk = true;
            String text = null;
            while ((text = r.readText()) != null) {
                refererUrl = adjustReferer(text, refererUrl, firstChunk);
                firstChunk = false;
                extractLinks(text, refererUrl, links);
            }
        }
    }

    //--- Non-public methods ---------------------------------------------------

    private String adjustReferer(
            final String content, final String refererUrl,
            final boolean firstChunk) {
        var ref = refererUrl;
        if (firstChunk) {
            var cntnt = content.replaceAll("\\s+", " ");
            var matcher = Pattern.compile(
                    "(?is)<base[^<]+?href\\s?=\\s?([\"']?)(.*?)\\1")
                        .matcher(cntnt);
            if (matcher.find()) {
                var baseUrl = matcher.group(2);
                if (StringUtils.isNotBlank(baseUrl)) {
                    ref = toCleanAbsoluteURL(refererUrl, baseUrl);
                }
            }
        }
        return ref;
    }

    private void extractLinks(
            String theContent, String referrerUrl, Set<Link> links) {
        var content = theContent;

        // Eliminate content not matching extract patterns
        content = excludeUnwantedContent(content);

        // make content easier to match by normalizing white spaces
        content = normalizeWhiteSpaces(content);

        // Get rid of <script> tags content to eliminate possibly
        // generated URLs.
        content = content.replaceAll(
                "(?is)(<script\\b[^>]*>)(.*?)(</script>)", "$1$3");

        // Possibly get rid of comments
        if (!isCommentsEnabled()) {
            content = content.replaceAll("(?is)<!--.*?-->", "");
        }

        Set<String> lcTagNames = new HashSet<>(tagAttribs
                .keySet()
                .stream()
                .map(String::toLowerCase)
                .toList());

        var tagNameMatcher = Pattern.compile("<(w+)").matcher(content);
        while (tagNameMatcher.find()) {
            var tag = parseTagMatch(content, tagNameMatcher.toMatchResult());
            tag.referrer = referrerUrl;
            if (!lcTagNames.contains(tag.name)) {
                continue;
            }
            tagLinksExtractor.test(tag, links);
        }
    }

    private Tag parseTagMatch(String content, MatchResult tagNameMatch) {
        var tag = new Tag();

        tag.name = tagNameMatch.group(1).toLowerCase();
        String attribsStr = null;
        var attribsMatcher = Pattern.compile("^(.*?)(/)?>").matcher(content);
        if (attribsMatcher.find(tagNameMatch.end())) {
            attribsStr = attribsMatcher.group(1);
            if (attribsMatcher.group(2) == null) { // not self-closed
                var m = Pattern.compile("(?i)^(.*?)</" + tag.name + ">")
                        .matcher(content);
                if (m.find(attribsMatcher.end())) {
                    tag.body = m.group(1);
                }
            }
        }

        if (attribsStr != null) {
            parseTagAttribs(tag, attribsStr);
        }

        tag.configAttribNames.addAll(tagAttribs.getStrings(tag.name));

        return tag;
    }

    private void parseTagAttribs(Tag tag, String attribsStr) {
        var m = Pattern.compile("^\\s*(\\w+)\\s*=\\s*([\"'])(.*?)\\2\\s*")
            .matcher(attribsStr);
        if (m.find()) {
            var name = m.group(1);
            var value = m.group(3);
            tag.attribs.add(name, value);
            parseTagAttribs(tag, StringUtils.substring(attribsStr, m.end()));
        }
    }

    private String normalizeWhiteSpaces(String content) {
        //MAYBE replacing all \s+ with " " in body might be ill advised.
        // make sure we do it on tags only.
        return content
                .replaceAll("\\s+", " ")
                .replace("< ", "<")
                .replace(" >", ">")
                .replace("</ ", "</")
                .replace("/ >", "/>");
    }

    // return same Link, for chaining
    private Link addMetadataToLink(Link link, Tag tag, String attWithUrl) {
        if (ignoreLinkData) {
            return link;
        }

        var linkMeta = link.getMetadata();

        setNonBlank(linkMeta, "tag", tag.name);
        setNonBlank(linkMeta, "text", tag.body);
        setNonBlank(linkMeta, "attr", attWithUrl);

        tag.attribs.forEach((attName, attValues) -> {
            if (!Objects.equal(attWithUrl, attName)) {
                attValues.forEach(
                        val -> setNonBlank(linkMeta, "attr." + attName, val));
            }
        });
        return link;
    }

    private void setNonBlank(Properties meta, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            meta.set(key.trim(), value.trim());
        }
    }

    //MAYBE: consider moving this logic to new class shared with others,
    //like StripBetweenTagger
    private String excludeUnwantedContent(String content) {
        var newContent = content;
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
        var b = new StringBuilder();
        for (RegexPair regexPair : extractBetweens) {
            for (Pair<Integer, Integer> pair :
                    matchBetweens(content, regexPair)) {
                b.append(content.substring(pair.getLeft(), pair.getRight()));
            }
        }
        return b.toString();
    }

    private String applyNoExtractBetweens(String content) {
        var b = new StringBuilder(content);
        for (RegexPair regexPair : noExtractBetweens) {
            var matches =
                    matchBetweens(content, regexPair);
            for (var i = matches.size() -1; i >= 0; i--) {
                var pair = matches.get(i);
                b.delete(pair.getLeft(), pair.getRight());
            }
        }
        return b.toString();
    }

    private List<Pair<Integer, Integer>> matchBetweens(
            String content, RegexPair pair) {
        var flags = Pattern.DOTALL;
        if (!pair.isCaseSensitive()) {
            flags = flags | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        }
        List<Pair<Integer, Integer>> matches = new ArrayList<>();
        var leftPattern = Pattern.compile(pair.getStart(), flags);
        var leftMatch = leftPattern.matcher(content);
        while (leftMatch.find()) {
            var rightPattern = Pattern.compile(pair.getEnd(), flags);
            var rightMatch = rightPattern.matcher(content);
            if (!rightMatch.find(leftMatch.end())) {
                break;
            }
            matches.add(new ImmutablePair<>(
                    leftMatch.start(), rightMatch.end()));
        }
        return matches;
    }

    private String applyExtractSelectors(String content) {
        var b = new StringBuilder();
        var doc = Jsoup.parse(content);
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
        var doc = Jsoup.parse(content);
        for (String selector : noExtractSelectors) {
            for (Element element : doc.select(selector)) {
                element.remove();
            }
        }
        return doc.toString();
    }

    private String toCleanAbsoluteURL(
            final String referrerUrl, final String newURL) {
        var url = StringUtils.trimToNull(newURL);
        if (!isValidNewURL(url)) {
            return null;
        }

        // Decode HTML entities.
        url = StringEscapeUtils.unescapeHtml4(url);

        // Revalidate after unescaping
        if (!isValidNewURL(url)) {
            return null;
        }

        url = HttpURL.toAbsolute(referrerUrl, url);

        if (url.length() > maxURLLength) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("""
                    URL length ({}) exceeding maximum length allowed\s\
                    ({}) to be extracted. URL (showing first {} chars):\s\
                    {}...""",
                        url.length(), maxURLLength, LOGGING_MAX_URL_LENGTH,
                        StringUtils.substring(url, 0, LOGGING_MAX_URL_LENGTH));
            }
            return null;
        }

        return url;
    }

    private boolean isValidNewURL(String newURL) {
        if (StringUtils.isBlank(newURL)) {
            return false;
        }

        // if scheme is specified, make sure it is valid
        if (newURL.matches("(?i)^[a-z][a-z0-9\\+\\.\\-]*:.*$")) {
            var supportedSchemes = getSchemes();
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
    protected void loadTextLinkExtractorFromXML(XML xml) {
        setMaxURLLength(xml.getInteger("@maxURLLength", maxURLLength));
        setIgnoreNofollow(xml.getBoolean("@ignoreNofollow", ignoreNofollow));
        setCommentsEnabled(xml.getBoolean("@commentsEnabled", commentsEnabled));
        setCharset(xml.getString("@charset", charset));
        setIgnoreLinkData(xml.getBoolean("@ignoreLinkData", ignoreLinkData));
        setSchemes(xml.getDelimitedStringList("schemes", schemes));
        loadTagsAndAttributes(xml);
        loadExtractAndNoExtractBetween(xml);
        loadExtractAndNoExtractSelectors(xml);
    }

    private void loadTagsAndAttributes(XML xml) {
        var xmlTagsParent = xml.getXML("tags");
        if (xmlTagsParent != null && xmlTagsParent.isEmpty()) {
            clearLinkTags();
        } else {
            var xmlTags = xml.getXMLList("tags/tag");
            if (!xmlTags.isEmpty()) {
                clearLinkTags();
                for (XML xmlTag: xmlTags) {
                    var name = xmlTag.getString("@name", null);
                    var attr = xmlTag.getString("@attribute", null);
                    if (StringUtils.isNotBlank(name)) {
                        addLinkTag(name, attr);
                    }
                }
            }
        }
    }

    private void loadExtractAndNoExtractBetween(XML xml) {
        // extract between
        var xmlBetweens = xml.getXMLList("extractBetween");
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
        var xmlNoBetweens = xml.getXMLList("noExtractBetween");
        if (!xmlNoBetweens.isEmpty()) {
            noExtractBetweens.clear();
            for (XML xmlNoBetween: xmlNoBetweens) {
                addNoExtractBetween(
                        xmlNoBetween.getString("start", null),
                        xmlNoBetween.getString("end", null),
                        xmlNoBetween.getBoolean("@caseSensitive", false));
            }
        }
    }

    private void loadExtractAndNoExtractSelectors(XML xml) {
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
        xml.setAttribute("maxURLLength", maxURLLength);
        xml.setAttribute("ignoreNofollow", ignoreNofollow);
        xml.setAttribute("commentsEnabled", commentsEnabled);
        xml.setAttribute("charset", charset);
        xml.setAttribute("ignoreLinkData", ignoreLinkData);

        xml.addDelimitedElementList("schemes", schemes);

        // Tags

        if (tagAttribs.isEmpty()) {
            xml.addElement("tags");
        } else {
            var xmlTags = xml.addElement("tags");
            for (Entry<String, List<String>> entry :
                    tagAttribs.entrySet()) {
                for (String attrib : entry.getValue()) {
                    xmlTags.addElement("tag")
                            .setAttribute("name", entry.getKey())
                            .setAttribute("attribute", attrib);
                }
            }
        }

        // extract between
        for (RegexPair pair : extractBetweens) {
            var xmlBetween = xml.addElement("extractBetween")
                    .setAttribute("caseSensitive", pair.caseSensitive);
            xmlBetween.addElement("start", pair.getStart());
            xmlBetween.addElement("end", pair.getEnd());
        }

        // no extract between
        for (RegexPair pair : noExtractBetweens) {
            var xmlNoBetween = xml.addElement("noExtractBetween")
                    .setAttribute("caseSensitive", pair.caseSensitive);
            xmlNoBetween.addElement("start", pair.getStart());
            xmlNoBetween.addElement("end", pair.getEnd());
        }

        // extract selector
        xml.addElementList("extractSelector", extractSelectors);

        // no extract selector
        xml.addElementList("noExtractSelector", noExtractSelectors);
    }

    //--- Inner Classes --------------------------------------------------------

    //MAYBE: make standalone class?
    @Data
    public static class RegexPair {
        private final String start;
        private final String end;
        private final boolean caseSensitive;
        public RegexPair(String start, String end, boolean caseSensitive) {
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
    }

    private static class Tag {
        private String name;
        private String body;
        private String referrer;
        private final Properties attribs = new Properties();
        private final List<String> configAttribNames = new ArrayList<>();
    }
}
