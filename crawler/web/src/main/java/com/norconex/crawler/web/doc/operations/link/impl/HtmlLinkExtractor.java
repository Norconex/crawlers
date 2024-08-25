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

import static com.norconex.crawler.web.doc.operations.link.impl.HtmlLinkExtractorConfig.HTTP_EQUIV;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.doc.operations.link.Link;
import com.norconex.crawler.web.doc.operations.link.LinkExtractor;
import com.norconex.crawler.web.doc.operations.link.impl.HtmlLinkExtractorConfig.RegexPair;
import com.norconex.crawler.web.doc.operations.url.WebUrlNormalizer;
import com.norconex.crawler.web.doc.operations.url.impl.GenericUrlNormalizer;
import com.norconex.crawler.web.util.Web;

import lombok.EqualsAndHashCode;
import lombok.Getter;
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
@Slf4j
@EqualsAndHashCode
@ToString
public class HtmlLinkExtractor
        implements LinkExtractor, Configurable<HtmlLinkExtractorConfig> {

    private static final int LOGGING_MAX_URL_LENGTH = 200;

    private static final String CONTENT = "content";

    //--- Properties -----------------------------------------------------------

    @Getter
    private final HtmlLinkExtractorConfig configuration =
            new HtmlLinkExtractorConfig();

    // NOTE: When this predicate is invoked the tag name is always lower case
    // and known to have been identified as a target tag name in configuration.
    // For each predicate, returning true won't try following predicates
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final BiPredicate<Tag, Set<Link>> tagLinksExtractor =

            //--- From tag body ---
            // When no attributes configured for a tag name, we take the body
            // value as the URL.
            ((BiPredicate<Tag, Set<Link>>) (tag, links) -> Optional.of(tag)
                    .filter(t -> t.configAttribNames.isEmpty())
                    .filter(t -> isNotBlank(t.bodyText))
                    .map(
                            t -> toCleanAbsoluteURL(
                                    t.referrer,
                                    tag.bodyText.trim()
                            )
                    )
                    .map(url -> addAsLink(links, url, tag, null))
                    .filter(Boolean::valueOf)
                    .orElse(false))

                            //--- From meta http-equiv tag ---
                            // E.g.: <meta http-equiv="refresh" content="...">:
                            .or(
                                    (tag, links) -> Optional.of(tag)
                                            .filter(t -> "meta".equals(t.name))
                                            .filter(
                                                    t -> t.configAttribNames
                                                            .contains(
                                                                    HTTP_EQUIV
                                                            )
                                            )
                                            .filter(
                                                    t -> t.attribs
                                                            .getStrings(
                                                                    HTTP_EQUIV
                                                            )
                                                            .contains("refresh")
                                            )
                                            .filter(
                                                    t -> t.attribs.containsKey(
                                                            CONTENT
                                                    )
                                            )
                                            // very unlikely that we have more than one redirect directives,
                                            // but loop just in case
                                            .map(
                                                    t -> t.attribs
                                                            .getStrings(CONTENT)
                                                            .stream()
                                                            .map(
                                                                    LinkUtil::extractHttpEquivRefreshContentUrl
                                                            )
                                                            .map(
                                                                    url -> toCleanAbsoluteURL(
                                                                            tag.referrer,
                                                                            url
                                                                    )
                                                            )
                                                            .findFirst()
                                                            .map(
                                                                    url -> addAsLink(
                                                                            links,
                                                                            url,
                                                                            tag,
                                                                            CONTENT
                                                                    )
                                                            )
                                                            .filter(
                                                                    Boolean::valueOf
                                                            )
                                                            .orElse(false)
                                            )
                                            .filter(Boolean::valueOf)
                                            .orElse(false)
                            )

                            //--- From anchor tag ---
                            // E.g.: <a href="...">...</a>
                            .or(
                                    (tag, links) -> Optional.of(tag)
                                            .filter(t -> "a".equals(t.name))
                                            .filter(
                                                    t -> t.configAttribNames
                                                            .contains("href")
                                            )
                                            .filter(
                                                    t -> t.attribs
                                                            .containsKey("href")
                                            )
                                            .filter(
                                                    t -> !hasActiveDoNotFollow(
                                                            t
                                                    )
                                            )
                                            .map(
                                                    t -> toCleanAbsoluteURL(
                                                            t.referrer,
                                                            t.attribs.getString(
                                                                    "href"
                                                            )
                                                    )
                                            )
                                            .map(
                                                    url -> addAsLink(
                                                            links, url, tag,
                                                            "href"
                                                    )
                                            )
                                            .filter(Boolean::valueOf)
                                            .orElse(hasActiveDoNotFollow(tag)) // skip others if no follow
                            )

                            //--- From other matching attributes for tag ---
                            .or(
                                    (tag, links) -> tag.configAttribNames
                                            .stream()
                                            .map(
                                                    cfgAttr -> Optional
                                                            .ofNullable(
                                                                    tag.attribs
                                                                            .getString(
                                                                                    cfgAttr
                                                                            )
                                                            )
                                                            .map(
                                                                    urlStr -> (EqualsUtil
                                                                            .equalsAny(
                                                                                    tag.name,
                                                                                    "object",
                                                                                    "applet"
                                                                            )
                                                                                    ? List.of(
                                                                                            StringUtils
                                                                                                    .split(
                                                                                                            urlStr,
                                                                                                            ", "
                                                                                                    )
                                                                                    )
                                                                                    : List.of(
                                                                                            urlStr
                                                                                    ))
                                                                                            .stream()
                                                                                            .map(
                                                                                                    url -> toCleanAbsoluteURL(
                                                                                                            tag.referrer,
                                                                                                            url
                                                                                                    )
                                                                                            )
                                                                                            .map(
                                                                                                    url -> addAsLink(
                                                                                                            links,
                                                                                                            url,
                                                                                                            tag,
                                                                                                            cfgAttr
                                                                                                    )
                                                                                            )
                                                                                            .anyMatch(
                                                                                                    Boolean::valueOf
                                                                                            )
                                                            )
                                            )
                                            .flatMap(Optional::stream)
                                            .anyMatch(Boolean::valueOf)
                            );

    @Override
    public Set<Link> extractLinks(CrawlDoc doc) throws IOException {

        // only proceed if we are dealing with a supported content type
        if (!configuration.getContentTypeMatcher().matches(
                doc.getDocContext().getContentType().toString()
        )) {
            return Set.of();
        }

        var refererUrl = doc.getReference();
        Set<Link> links = new HashSet<>();

        if (configuration.getFieldMatcher().isSet()) {
            // Fields
            doc.getMetadata()
                    .matchKeys(configuration.getFieldMatcher())
                    .valueList()
                    .forEach(
                            val -> extractLinksFromText(
                                    links, val, refererUrl,
                                    true
                            )
                    );
        } else {
            // Body
            try (var r = new TextReader(
                    new InputStreamReader(doc.getInputStream())
            )) {
                var firstChunk = true;
                String text = null;
                while ((text = r.readText()) != null) {
                    extractLinksFromText(links, text, refererUrl, firstChunk);
                }
            }
        }
        return links;
    }

    //--- Non-public methods ---------------------------------------------------

    private void extractLinksFromText(
            Set<Link> links, String text, String url, boolean checkBaseHref
    ) {
        var content = normalizeWhiteSpaces(text);
        var refererUrl = adjustReferer(content, url, checkBaseHref);
        extractLinksFromCleanText(links, content, refererUrl);
    }

    private String adjustReferer(
            final String content, final String refererUrl,
            final boolean firstChunk
    ) {
        var ref = refererUrl;
        if (firstChunk) {
            // make content easier to match by normalizing white spaces
            var cntnt = content.replace(" =", "=").replace("= ", "=");
            var matcher = Pattern.compile("(?is)<base\\b([^<>]+)>")
                    .matcher(cntnt);
            if (matcher.find()) {
                var attribs = Web.parseDomAttributes(matcher.group(1), true);
                var baseUrl = attribs.getString("href");
                if (StringUtils.isNotBlank(baseUrl)) {
                    ref = toCleanAbsoluteURL(refererUrl, baseUrl);
                }
            }
        }
        return ref;
    }

    private void extractLinksFromCleanText(
            Set<Link> links, String theContent, String referrerUrl
    ) {
        var content = theContent;

        // Eliminate content not matching extract patterns
        content = excludeUnwantedContent(content);

        // Get rid of <script> tags content to eliminate possibly
        // generated URLs.
        content = content.replaceAll(
                "(?is)(<script\\b[^>]*>)(.*?)(</script>)", "$1$3"
        );

        // Possibly get rid of comments
        if (!configuration.isCommentsEnabled()) {
            content = content.replaceAll("(?is)<!--.*?-->", "");
        }

        Set<String> lcTagNames = new HashSet<>(
                configuration.getTagAttribs()
                        .keySet()
                        .stream()
                        .map(String::toLowerCase)
                        .toList()
        );

        var tagNameMatcher = Pattern.compile("<([\\w-]+)").matcher(content);
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

        var attribsMatcher = Pattern
                .compile("^(.*?)(/)?>")
                .matcher(content)
                .region(tagNameMatch.end(), content.length());
        if (attribsMatcher.find()) {
            attribsStr = attribsMatcher.group(1);
            if (attribsMatcher.group(2) == null) { // not self-closed
                var m = Pattern
                        .compile("(?i)^(.*?)</" + tag.name + ">")
                        .matcher(content)
                        .region(attribsMatcher.end(), content.length());
                if (m.find()) {
                    var markup = m.group(1);
                    tag.bodyText = markup.replaceAll("<[^<>]+>", "");
                    if (!tag.bodyText.equals(markup)) {
                        tag.bodyMarkup = markup;
                    }
                }
            }
        }

        tag.attribs.putAll(Web.parseDomAttributes(attribsStr));

        tag.configAttribNames.addAll(
                configuration.getTagAttribs()
                        .getStrings(tag.name)
                        .stream()
                        .filter(StringUtils::isNotBlank)
                        .toList()
        );

        return tag;
    }

    private String normalizeWhiteSpaces(String content) {
        //MAYBE can replacing all \s+ with " " in body even just for URL
        // extraction be ill-advised? Make sure we do it on tags only?
        return content
                .replaceAll("\\s+", " ")
                .replace("< ", "<")
                .replace(" >", ">")
                .replace("</ ", "</")
                .replace("/ >", "/>");
    }

    private boolean addAsLink(
            Set<Link> links, String url, Tag tag, String attWithUrl
    ) {
        if (StringUtils.isBlank(url)) {
            return false;
        }

        var link = new Link(url);
        link.setReferrer(tag.referrer);

        if (configuration.isIgnoreLinkData()) {
            return links.add(link);
        }

        var linkMeta = link.getMetadata();

        setNonBlank(linkMeta, "tag", tag.name);
        setNonBlank(linkMeta, "text", tag.bodyText);
        setNonBlank(linkMeta, "markup", tag.bodyMarkup);
        setNonBlank(linkMeta, "attr", attWithUrl);

        tag.attribs.forEach((attName, attValues) -> {
            if (!Objects.equal(attWithUrl, attName)) {
                attValues.forEach(
                        val -> setNonBlank(linkMeta, "attr." + attName, val)
                );
            }
        });
        return links.add(link);
    }

    private boolean hasActiveDoNotFollow(Tag tag) {
        return "a".equals(tag.name)
                && !configuration.isIgnoreNofollow()
                && tag.attribs.getStrings("rel")
                        .stream()
                        .anyMatch(
                                s -> "nofollow"
                                        .equalsIgnoreCase(trimToEmpty(s))
                        );
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
        if (!configuration.getExtractBetweens().isEmpty()) {
            newContent = applyExtractBetweens(newContent);
        }
        if (!configuration.getNoExtractBetweens().isEmpty()) {
            newContent = applyNoExtractBetweens(newContent);
        }
        if (!configuration.getExtractSelectors().isEmpty()) {
            newContent = applyExtractSelectors(newContent);
        }
        if (!configuration.getNoExtractSelectors().isEmpty()) {
            newContent = applyNoExtractSelectors(newContent);
        }
        return newContent;
    }

    private String applyExtractBetweens(String content) {
        var b = new StringBuilder();
        for (RegexPair regexPair : configuration.getExtractBetweens()) {
            for (Pair<Integer, Integer> pair : matchBetweens(
                    content,
                    regexPair
            )) {
                b.append(content.substring(pair.getLeft(), pair.getRight()));
            }
        }
        return b.toString();
    }

    private String applyNoExtractBetweens(String content) {
        var b = new StringBuilder(content);
        for (RegexPair regexPair : configuration.getNoExtractBetweens()) {
            var matches =
                    matchBetweens(content, regexPair);
            for (var i = matches.size() - 1; i >= 0; i--) {
                var pair = matches.get(i);
                b.delete(pair.getLeft(), pair.getRight());
            }
        }
        return b.toString();
    }

    private List<Pair<Integer, Integer>> matchBetweens(
            String content, RegexPair pair
    ) {
        var flags = Pattern.DOTALL;
        if (pair.isIgnoreCase()) {
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
            matches.add(
                    new ImmutablePair<>(
                            leftMatch.start(), rightMatch.end()
                    )
            );
        }
        return matches;
    }

    private String applyExtractSelectors(String content) {
        var b = new StringBuilder();
        var doc = Jsoup.parse(content);
        for (String selector : configuration.getExtractSelectors()) {
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
        for (String selector : configuration.getNoExtractSelectors()) {
            for (Element element : doc.select(selector)) {
                element.remove();
            }
        }
        return doc.toString();
    }

    private String toCleanAbsoluteURL(
            final String referrerUrl, final String newURL
    ) {
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

        if (url.length() > configuration.getMaxURLLength()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(
                        """
                                URL length ({}) exceeding maximum length allowed\s\
                                ({}) to be extracted. URL (showing first {} chars):\s\
                                {}...""",
                        url.length(),
                        configuration.getMaxURLLength(),
                        LOGGING_MAX_URL_LENGTH,
                        StringUtils.substring(url, 0, LOGGING_MAX_URL_LENGTH)
                );
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
            var supportedSchemes = configuration.getSchemes();
            if (supportedSchemes.isEmpty()) {
                supportedSchemes = HtmlLinkExtractorConfig.DEFAULT_SCHEMES;
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

    //--- Inner Classes --------------------------------------------------------

    @EqualsAndHashCode
    @ToString
    private static class Tag {
        private String name;
        private String bodyText;
        private String bodyMarkup;
        private String referrer;
        private final Properties attribs = new Properties();
        private final List<String> configAttribNames = new ArrayList<>();
    }
}
