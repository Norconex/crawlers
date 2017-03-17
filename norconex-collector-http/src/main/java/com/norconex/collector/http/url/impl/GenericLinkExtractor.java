/* Copyright 2014-2017 Norconex Inc.
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
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.tika.utils.CharsetUtils;

import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.collector.http.url.Link;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;
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
 * By default, a regular HTML link having the "rel" attribute set to "nofollow"
 * won't be extracted (e.g. 
 * <code>&lt;a href="x.html" rel="nofollow" ...&gt;</code>).  
 * To force its extraction (and ensure it is followed) you can set 
 * {@link #setIgnoreNofollow(boolean)} to <code>true</code>.
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
 *      &lt;!-- Which tags and attributes hold the URLs to extract --&gt;
 *      &lt;tags&gt;
 *          &lt;tag name="(tag name)" attribute="(tag attribute)" /&gt;
 *          &lt;!-- you can have multiple tag entries --&gt;
 *      &lt;/tags&gt;
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

    private static final Logger LOG = LogManager.getLogger(
            GenericLinkExtractor.class);

    //TODO make buffer size and overlap size configurable
    //1MB: make configurable
    public static final int MAX_BUFFER_SIZE = 1024 * 1024;
    // max url leng is 2048 x 2 bytes x 2 for <a> anchor attributes.
    public static final int OVERLAP_SIZE = 2 * 2 * 2048;

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;

    private static final ContentType[] DEFAULT_CONTENT_TYPES = 
            new ContentType[] {
        ContentType.HTML,
        ContentType.valueOf("application/xhtml+xml"),
        ContentType.valueOf("vnd.wap.xhtml+xml"),
        ContentType.valueOf("x-asp"),
    };
    private static final String[] DEFAULT_SCHEMES = 
            new String[] { "http", "https", "ftp" };
    
    private static final int PATTERN_URL_GROUP = 4;
    private static final int PATTERN_FLAGS = 
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final int LOGGING_MAX_URL_LENGTH = 200;
    
    private ContentType[] contentTypes = DEFAULT_CONTENT_TYPES;
    private String[] schemes = DEFAULT_SCHEMES;
    private int maxURLLength = DEFAULT_MAX_URL_LENGTH;
    private boolean ignoreNofollow;
    private final Properties tagAttribs = new Properties(true);
    private Pattern tagPattern;
    private String charset;
    private boolean commentsEnabled;
    
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

        ContentType[] cTypes = contentTypes;
        if (ArrayUtils.isEmpty(cTypes)) {
            cTypes = DEFAULT_CONTENT_TYPES;
        }

        // Do not extract if not a supported content type
        if (!ArrayUtils.contains(cTypes, contentType)) {
            return null;
        }

        // Do it, extract Links
        String sourceCharset = getCharset();
        if (StringUtils.isBlank(sourceCharset)) {
            sourceCharset = CharsetUtil.detectCharset(input);
        } else {
            sourceCharset = CharsetUtils.clean(sourceCharset);
        }
        sourceCharset = 
                StringUtils.defaultIfBlank(sourceCharset, CharEncoding.UTF_8);
        
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
        if (ArrayUtils.isEmpty(contentTypes)) {
            return true;
        }
        return ArrayUtils.contains(contentTypes, contentType);
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

    public ContentType[] getContentTypes() {
        return ArrayUtils.clone(contentTypes);
    }
    public void setContentTypes(ContentType... contentTypes) {
        this.contentTypes = ArrayUtils.clone(contentTypes);
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
    public String[] getSchemes() {
        return schemes;
    }
    /**
     * Sets the schemes to be extracted.
     * @param schemes schemes to be extracted
     * @since 2.4.0
     */
    public void setSchemes(String... schemes) {
        this.schemes = schemes;
    }

    public boolean isIgnoreNofollow() {
        return ignoreNofollow;
    }
    public void setIgnoreNofollow(boolean ignoreNofollow) {
        this.ignoreNofollow = ignoreNofollow;
    }

    /**
     * Gets whether to keep referrer data. 
     * <b>Since 2.6.0, always return true</b>.
     * @return <code>true</code>
     * @deprecated Since 2.6.0, referrer data is always kept
     */
    @Deprecated
    public boolean isKeepReferrerData() {
        return true;
    }
    /**
     * Sets whether to keep the referrer data. 
     * <b>Since 2.6.0, this method has no effect.</b>  
     * @param keepReferrerData referrer data
     * @deprecated Since 2.6.0, referrer data is always kept
     */
    @Deprecated
    public void setKeepReferrerData(boolean keepReferrerData) {
        LOG.warn("Since 2.6.0, referrer data is always kept. "
               + "Setting \"keepReferrerData\" has no effect.");
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
        tagAttribs.addString(tagName, attribute);
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
                tagAttribs.setString(tagName, 
                        values.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
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
                    "(^|\\s)(" + attribs + ")\\s*=\\s*([\"'])([^\\<\\>]*?)\\3",
                    PATTERN_FLAGS);
            Matcher urlMatcher = p.matcher(restOfTag);
            while (urlMatcher.find()) {
                String attribName = urlMatcher.group(2);
                String matchedUrl = urlMatcher.group(PATTERN_URL_GROUP);
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
        String url = toCleanAbsoluteURL(referrer, m.group(PATTERN_URL_GROUP));
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
            LOG.debug("URL length (" + url.length() + ") exceeding "
                   + "maximum length allowed (" + maxURLLength
                   + ") to be extracted. URL (showing first "
                   + LOGGING_MAX_URL_LENGTH + " chars): " 
                   + StringUtils.substring(
                           url, 0, LOGGING_MAX_URL_LENGTH) + "...");
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
            String[] supportedSchemes = getSchemes();
            if (ArrayUtils.isEmpty(supportedSchemes)) {
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
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = XMLConfigurationUtil.newXMLConfiguration(in);
        setMaxURLLength(xml.getInt("[@maxURLLength]", getMaxURLLength()));
        setIgnoreNofollow(xml.getBoolean(
                "[@ignoreNofollow]", isIgnoreNofollow()));
        setCommentsEnabled(xml.getBoolean(
                "[@commentsEnabled]", isCommentsEnabled()));
        setCharset(xml.getString("[@charset]", getCharset()));
        if (xml.getBoolean("[@keepFragment]", false)) {
            LOG.warn("'keepFragment' on GenericLinkExtractor was removed. "
                   + "Instead, URL normalization now always takes place by "
                   + "default unless disabled, and removeFragment is part of "
                   + "the default normalization rules.");
        }
        
        // Content Types
        ContentType[] cts = ContentType.valuesOf(StringUtils.split(
                StringUtils.trimToNull(xml.getString("contentTypes")), ", "));
        if (!ArrayUtils.isEmpty(cts)) {
            setContentTypes(cts);
        }

        // Schemes
        String[] supportedSchemes = StringUtils.split(
                StringUtils.trimToNull(xml.getString("schemes")), ", ");
        if (!ArrayUtils.isEmpty(supportedSchemes)) {
            setSchemes(supportedSchemes);
        }

        // tag & attributes
        List<HierarchicalConfiguration> tagNodes = 
                xml.configurationsAt("tags.tag");
        if (!tagNodes.isEmpty()) {
            clearLinkTags();
            for (HierarchicalConfiguration tagNode : tagNodes) {
                String name = tagNode.getString("[@name]", null);
                String attr = tagNode.getString("[@attribute]", null);
                if (StringUtils.isNotBlank(name)) {
                    addLinkTag(name, attr);
                }
            }
        }
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeStartElement("extractor");
            writer.writeAttribute("class", getClass().getCanonicalName());

            writer.writeAttributeInteger("maxURLLength", getMaxURLLength());
            writer.writeAttributeBoolean("ignoreNofollow", isIgnoreNofollow());
            writer.writeAttributeBoolean(
                    "commentsEnabled", isCommentsEnabled());
            writer.writeAttributeString("charset", getCharset());
            
            // Content Types
            if (!ArrayUtils.isEmpty(getContentTypes())) {
                writer.writeElementString("contentTypes", 
                        StringUtils.join(getContentTypes(), ','));
            }

            // Schemes
            if (!ArrayUtils.isEmpty(getSchemes())) {
                writer.writeElementString("schemes", 
                        StringUtils.join(getSchemes(), ','));
            }

            // Tags
            writer.writeStartElement("tags");
            for (Map.Entry<String, List<String>> entry : 
                    tagAttribs.entrySet()) {
                for (String attrib : entry.getValue()) {
                    writer.writeStartElement("tag");
                    writer.writeAttributeString("name", entry.getKey());
                    writer.writeAttributeString("attribute", attrib);
                    writer.writeEndElement();
                }
            }
            writer.writeEndElement();

            writer.writeEndElement();
            writer.flush();
            writer.close();
            
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
        
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
            if (LOG.isDebugEnabled()) {
                LOG.debug("DOCUMENT URL ----> " + documentUrl);
                LOG.debug("  BASE RELATIVE -> " + relativeBase);
                LOG.debug("  BASE ABSOLUTE -> " + absoluteBase);
            }
        }
    }

    
    
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("contentTypes", contentTypes)
                .append("schemes", schemes)
                .append("maxURLLength", maxURLLength)
                .append("ignoreNofollow", ignoreNofollow)
                .append("commentsEnabled", commentsEnabled)
                .append("tagAttribs", tagAttribs)
                .append("charset", charset)
                .toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof GenericLinkExtractor)) {
            return false;
        }
        
        GenericLinkExtractor castOther = (GenericLinkExtractor) other;
        return new EqualsBuilder()
                .append(contentTypes, castOther.contentTypes)
                .append(schemes, castOther.schemes)
                .append(maxURLLength, castOther.maxURLLength)
                .append(ignoreNofollow, castOther.ignoreNofollow)
                .append(commentsEnabled, castOther.commentsEnabled)
                .append(tagAttribs.entrySet(), castOther.tagAttribs.entrySet())
                .append(charset, castOther.charset)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(contentTypes)
                .append(schemes)
                .append(maxURLLength)
                .append(ignoreNofollow)
                .append(commentsEnabled)
                .append(tagAttribs)
                .append(charset)
                .toHashCode();
    }
}
