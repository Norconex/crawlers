/* Copyright 2014 Norconex Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.collector.http.url.Link;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * Generic link extractor for URLs found in HTML files.   
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
 * URLs are assumed to be contained within valid tag attributes.  
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
 * The <code>meta.http-equiv</code> is treated differently.  Only if the
 * "http-equiv" value is refresh and a "content" tag with a URL exist that it
 * will be extracted.  "object" and "applet" can have multiple URLs.
 *
 * <h3>Referrer data</h3>
 * You can optionally set {@link #setKeepReferrerData(boolean)} 
 * to <code>true</code> to 
 * have the following referrer information stored as metadata in each document
 * represented by the extracted URLs:
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
 * 
 * <h3>"nofollow"</h3>
 * By default, a regular HTML link having the "rel" attribute set to "nofollow"
 * won't be extracted (e.g. <code>&lt;a href="x.html" rel="nofollow" ...</code>.  
 * To force its extraction (and ensure it followed) you can set 
 * {@link #setIgnoreNofollow(boolean)} to <code>true</code>.
 * 
 * <h3>XML configuration usage</h3>
 * <pre>
 *  &lt;extractor class="com.norconex.collector.http.url.impl.HtmlLinkExtractor"
 *          maxURLLength="(maximum URL length. Default is 2048)" 
 *          ignoreNofollow="(false|true)" 
 *          keepReferrerData="(false|true)"&gt;
 *      &lt;contentTypes&gt;
 *          (CSV list of content types on which to perform link extraction.
 *           leave blank or remove tag to use defaults.)
 *      &lt;/contentTypes&gt;
 *      
 *      &lt;!-- Which tags and attributes hold the URLs to extract --&gt;
 *      &lt;tags&gt;
 *          &lt;tag name="(tag name)" attribute="tag attribute)" /&gt;
 *          &lt;!-- you can have multiple tag entries --&gt;
 *      &lt;/tags&gt;
 *  &lt;/extractor&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class HtmlLinkExtractor implements ILinkExtractor, IXMLConfigurable {

    private static final Logger LOG = LogManager.getLogger(
            HtmlLinkExtractor.class);

    //TODO make buffer size and overlap size configurable
    //1MB: make configurable
    public static final int MAX_BUFFER_SIZE = 1024 * 1024;
    // max url leng is 2048 x 2 bytes + x2 for <a> anchor attributes.
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
    private static final int INPUT_READ_ARRAY_SIZE = 2048;
    private static final int PATTERN_URL_GROUP = 4;
    private static final int PATTERN_FLAGS = 
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final int LOGGING_MAX_URL_LENGTH = 200;
    
    private ContentType[] contentTypes = DEFAULT_CONTENT_TYPES;
    private int maxURLLength = DEFAULT_MAX_URL_LENGTH;
    private boolean ignoreNofollow;
    private boolean keepReferrerData;
    private final Properties tagAttribs = new Properties();
    private Pattern tagPattern;
    
    public HtmlLinkExtractor() {
        super();
        // default tags/attributes used to extract data. 
        addLinkTag("a", "href");
        addLinkTag("frame", "src");
        addLinkTag("iframe", "src");
        addLinkTag("img", "src");
        addLinkTag("meta", "http-equiv");
    }

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
        Referer urlParts = new Referer(reference);
        Set<Link> links = new HashSet<>();
        
        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[INPUT_READ_ARRAY_SIZE];
        int length;
        while ((length = input.read(buffer)) != -1) {
            sb.append(new String(buffer, 0, length, CharEncoding.UTF_8));
            if (sb.length() >= MAX_BUFFER_SIZE) {
                extractLinks(sb.toString(), urlParts, links);
                sb.delete(0, sb.length() - OVERLAP_SIZE);
            }
        }
        extractLinks(sb.toString(), urlParts, links);
        sb.setLength(0);
        
        return links;
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

    public boolean isIgnoreNofollow() {
        return ignoreNofollow;
    }
    public void setIgnoreNofollow(boolean ignoreNofollow) {
        this.ignoreNofollow = ignoreNofollow;
    }
    
    public boolean isKeepReferrerData() {
        return keepReferrerData;
    }
    public void setKeepReferrerData(boolean keepReferrerData) {
        this.keepReferrerData = keepReferrerData;
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
        tagPattern = Pattern.compile("<(" + tagNames + ")\\s([^\\<]*?)>",
                PATTERN_FLAGS);
    }
    
    //--- Extract Links --------------------------------------------------------
    private static final Pattern A_TEXT_PATTERN = Pattern.compile(
            "<a[^<]+?>([^<]+?)<\\s*/\\s*a\\s*>", PATTERN_FLAGS);
    private void extractLinks(
            String content, Referer referrer, Set<Link> links) {
        
        Matcher matcher = tagPattern.matcher(content);
        while (matcher.find()) {
            String tagName = matcher.group(1);
            String restOfTag = matcher.group(2);
            String text = null;
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
                if (keepReferrerData) {
                    Matcher textMatcher = A_TEXT_PATTERN.matcher(content);
                    if (textMatcher.find(matcher.start())) {
                        text = textMatcher.group(1).trim();
                    }
                }
            }
            String attribs = tagAttribs.getString(tagName);
            Pattern p = Pattern.compile(
                    "(^|\\s)(" + attribs + ")\\s*=\\s*([\"'])([^\\<\\>]+?)\\3",
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
                    url = toAbsoluteURL(referrer, url);
                    if (url == null) {
                        continue;
                    }
                    Link link = new Link(url);
                    if (keepReferrerData) {
                        link.setReferrer(referrer.url);
                        link.setTag(tagName + "." + attribName);
                        link.setText(text);
                    }
                    links.add(link);
                }
            }
        }
    }
    
    //--- Extract meta refresh -------------------------------------------------
    private static final Pattern META_EQUIV_REFRESH_PATTERN = Pattern.compile(
            "(^|\\W+)http-equiv\\s*=\\s*[\"']refresh[\"']", PATTERN_FLAGS);
    private static final Pattern META_CONTENT_URL_PATTERN = Pattern.compile(
            "(^|\\W+)content\\s*=\\s*([\"'])[^a-zA-Z]*url"
          + "\\s*=\\s*([\"']{0,1})([^\\<\\>]+?)\\3.*?\\2", PATTERN_FLAGS);
    private void extractMetaRefresh(
            String restOfTag, Referer referrer, Set<Link> links) {
        if (!META_EQUIV_REFRESH_PATTERN.matcher(restOfTag).find()) {
            return;
        }
        Matcher m = META_CONTENT_URL_PATTERN.matcher(restOfTag);
        if (!m.find()) {
            return;
        }
        String url = toAbsoluteURL(referrer, m.group(PATTERN_URL_GROUP));
        Link link = new Link(url);
        if (keepReferrerData) {
            link.setReferrer(referrer.url);
            link.setTag("meta.http-equiv.refresh");
        }
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
    
    
    private String toAbsoluteURL(final Referer urlParts, final String newURL) {
        if (!isValidNewURL(newURL)) {
            return null;
        }
        String url = newURL;
        if (url.startsWith("//")) {
            // this is URL relative to protocol
            url = urlParts.protocol 
                    + StringUtils.substringAfter(url, "//");
        } else if (url.startsWith("/")) {
            // this is a URL relative to domain name
            url = urlParts.absoluteBase + url;
        } else if (url.startsWith("?") || url.startsWith("#")) {
            // this is a relative url and should have the full page base
            url = urlParts.documentBase + url;
        } else if (!url.contains("://")) {
            if (urlParts.relativeBase.endsWith("/")) {
                // This is a URL relative to the last URL segment
                url = urlParts.relativeBase + url;
            } else {
                url = urlParts.relativeBase + "/" + url;
            }
        }
        //TODO have configurable whether to strip anchors.
        url = StringUtils.substringBefore(url, "#");
        
        if (url.length() > maxURLLength) {
            LOG.debug("URL length (" + url.length() + ") exeeding "
                   + "maximum length allowed (" + maxURLLength
                   + ") to be extracted. URL (showing first "
                   + LOGGING_MAX_URL_LENGTH + " chars): " 
                   + StringUtils.substring(
                           url, 0, LOGGING_MAX_URL_LENGTH) + "...");
            return null;
        }
        return url;
    }

    private boolean isValidNewURL(String newURL) {
        if (StringUtils.isBlank(newURL)) {
            return false;
        }
        if (StringUtils.startsWithIgnoreCase(newURL, "mailto:")) {
            return false;
        }
        if (StringUtils.startsWithIgnoreCase(newURL, "javascript:")) {
            return false;
        }
        return true;
    }

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
        setMaxURLLength(xml.getInt("[@maxURLLength]", getMaxURLLength()));
        setIgnoreNofollow(
                xml.getBoolean("[@ignoreNofollow]", isIgnoreNofollow()));
        setKeepReferrerData(
                xml.getBoolean("[@keepReferrerData]", isKeepReferrerData()));

        // Content Types
        ContentType[] cts = ContentType.valuesOf(StringUtils.split(
                StringUtils.trimToNull(xml.getString("contentTypes")), ", "));
        if (!ArrayUtils.isEmpty(cts)) {
            setContentTypes(cts);
        }
        
        // tag & attributes
        List<HierarchicalConfiguration> tagNodes = 
                xml.configurationsAt("tags.tag");
        if (!tagNodes.isEmpty()) {
            clearLinkTags();
            for (HierarchicalConfiguration tagNode : tagNodes) {
                String name = tagNode.getString("[@name]", null);
                String attr = tagNode.getString("[@attribute]", null);
                if (!StringUtils.isAnyBlank(name, attr)) {
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
                    "keepReferrerData", isKeepReferrerData());

            // Content Types
            if (!ArrayUtils.isEmpty(getContentTypes())) {
                writer.writeElementString("contentTypes", 
                        StringUtils.join(getContentTypes(), ','));
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
    
    private static class Referer {
        private final String protocol;
        private final String path;
        private final String relativeBase;
        private final String absoluteBase;
        private final String documentBase;
        private final String url;
        public Referer(String documentUrl) {
            super();
            this.url = documentUrl;
            //TODO SHOULD WE/HOW TO HANDLE <BASE> tag?
            
            // URL Protocol/scheme, up to double slash (included)
            protocol = documentUrl.replaceFirst("(.*?://)(.*)", "$1");

            // URL Path (anything after double slash)
            path = documentUrl.replaceFirst("(.*?://)(.*)", "$2");
            
            // URL Relative Base: truncate to last / before a ? or #
            String relBase = path.replaceFirst(
                    "(.*?)([\\?\\#])(.*)", "$1");
            relativeBase = protocol +  relBase.replaceFirst("(.*/)(.*)", "$1");

            // URL Absolute Base: truncate to first / if present, after protocol
            absoluteBase = protocol + path.replaceFirst("(.*?)(/.*)", "$1");
            
            // URL Document Base: truncate from first ? or # 
            documentBase = 
                    protocol + path.replaceFirst("(.*?)([\\?\\#])(.*)", "$1");
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
                .append("maxURLLength", maxURLLength)
                .append("ignoreNofollow", ignoreNofollow)
                .append("keepReferrerData", keepReferrerData)
                .append("tagAttribs", tagAttribs).toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof HtmlLinkExtractor)) {
            return false;
        }
        
        HtmlLinkExtractor castOther = (HtmlLinkExtractor) other;
        return new EqualsBuilder().append(contentTypes, castOther.contentTypes)
                .append(maxURLLength, castOther.maxURLLength)
                .append(ignoreNofollow, castOther.ignoreNofollow)
                .append(keepReferrerData, castOther.keepReferrerData)
                .isEquals() &&  CollectionUtils.containsAll(
                        tagAttribs.entrySet(), castOther.tagAttribs.entrySet());
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(contentTypes).append(maxURLLength)
                .append(ignoreNofollow).append(keepReferrerData)
                .append(tagAttribs).toHashCode();
    }
}
