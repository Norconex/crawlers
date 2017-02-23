/* Copyright 2017 Norconex Inc.
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
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
import com.norconex.collector.http.url.Link;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;
import com.norconex.importer.util.CharsetUtil;

/**
 * <p>
 * Link extractor using regular expressions to extract links found in text
 * documents. Relative links are resolved to the document URL.
 * For HTML documents, it is best advised to use the 
 * {@link GenericLinkExtractor}, which addresses many cases specific to HTML.
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
 * You can specify your own content types or reference restriction patterns
 * using {@link #setApplyToContentTypePattern(String)} or
 * {@link #setApplyToReferencePattern(String)}, but make sure they 
 * represent text files. When both methods are used, a document should be
 * be matched by both to be accepted.
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
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;extractor class="com.norconex.collector.http.url.impl.RegexLinkExtractor"
 *          maxURLLength="(maximum URL length. Default is 2048)" 
 *          charset="(supported character encoding)" &gt;
 *      &lt;applyToContentTypePattern&gt;
 *          (Regular expression matching content types this extractor 
 *           should apply to. Default accepts "text/.*".)
 *      &lt;/applyToContentTypePattern&gt;
 *      &lt;applyToReferencePattern&gt;
 *          (Regular expression matching references this extractor should
 *           apply to. Default accepts all references.)
 *      &lt;/applyToReferencePattern&gt;
 *      
 *      &lt;!-- Patterns for URLs to extract --&gt;
 *      &lt;linkExtractionPatterns&gt;
 *          &lt;pattern group="(match group index for the URL)"&gt;
 *            (regular expression)
 *          &lt;/pattern&gt;
 *          &lt;!-- you can have multiple pattern entries --&gt;
 *      &lt;/linkExtractionPatterns&gt;
 *  &lt;/extractor&gt;
 * </pre>
 * 
 * <h4>Usage example:</h4>
 * <p>
 * The following extracts URLs contained within square brackets.

 * </p>
 * <pre>
 *  &lt;extractor class="com.norconex.collector.http.url.impl.RegexLinkExtractor"&gt;
 *      &lt;linkExtractionPatterns&gt;
 *          &lt;pattern group="1"&gt;\[(http.*?)\]&lt;/pattern&gt;
 *      &lt;/linkExtractionPatterns&gt;
 *  &lt;/extractor&gt;
 * </pre>
 * 
 * @author Pascal Essiembre
 * @since 2.7.0
 */
public class RegexLinkExtractor implements ILinkExtractor, IXMLConfigurable {

    private static final Logger LOG = LogManager.getLogger(
            RegexLinkExtractor.class);

    public static final String DEFAULT_CONTENT_TYPE_PATTERN = "text/.*";

    //TODO make buffer size and overlap size configurable
    //1MB: make configurable
    public static final int MAX_BUFFER_SIZE = 1024 * 1024;
    // max url leng is 2048 x 2 bytes x 2 for <a> anchor attributes.
    public static final int OVERLAP_SIZE = 2 * 2 * 2048;

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;

    private static final int LOGGING_MAX_URL_LENGTH = 200;
    
    private int maxURLLength = DEFAULT_MAX_URL_LENGTH;
    private String charset;
    private String applyToContentTypePattern = DEFAULT_CONTENT_TYPE_PATTERN;
    private String applyToReferencePattern;
    private final Map<String, Integer> patterns = new HashMap<>();
    
    public RegexLinkExtractor() {
        super();
    }

    @Override
    public Set<Link> extractLinks(InputStream input, String reference,
            ContentType contentType) throws IOException {

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
        while ((ch = r.read()) != -1) {
            sb.append((char) ch);
            if (sb.length() >= MAX_BUFFER_SIZE) {
                String content = sb.toString();
                extractLinks(content, referer, links);
                sb.delete(0, sb.length() - OVERLAP_SIZE);
            }
        }
        String content = sb.toString();
        extractLinks(content, referer, links);
        sb.setLength(0);
        return links;
    }
    
    
    @Override
    public boolean accepts(String url, ContentType contentType) {
        if (StringUtils.isNotBlank(applyToReferencePattern)
                && !Pattern.matches(applyToReferencePattern, url)) {
            return false;
        }
        if (StringUtils.isNotBlank(applyToContentTypePattern)
                && !Pattern.matches(applyToContentTypePattern, 
                        contentType.toString())) {
            return false;
        }
        return true;
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

    public String getApplyToContentTypePattern() {
        return applyToContentTypePattern;
    }
    public void setApplyToContentTypePattern(String applyToContentTypePattern) {
        this.applyToContentTypePattern = applyToContentTypePattern;
    }

    public String getApplyToReferencePattern() {
        return applyToReferencePattern;
    }
    public void setApplyToReferencePattern(String applyToReferencePattern) {
        this.applyToReferencePattern = applyToReferencePattern;
    }

    public List<String> getPatterns() {
        return new ArrayList<>(patterns.keySet());
    }
    public int getPatternMatchGroup(String pattern) {
        Integer i = patterns.get(pattern);
        if (i == null) {
            return -1;
        }
        return i;
    }
    public void clearPatterns() {
        this.patterns.clear();
    }
    public void addPattern(String pattern) {
        this.patterns.put(pattern, 0);
    }
    public void addPattern(String pattern, int matchGroup) {
        this.patterns.put(pattern, matchGroup);
    }

    private void extractLinks(
            String content, Referer referrer, Set<Link> links) {
        for (Entry<String, Integer> e: patterns.entrySet()) {
            String pattern = e.getKey();
            int matchGroup = e.getValue();
            Matcher matcher = Pattern.compile(pattern).matcher(content);
            while (matcher.find()) {
                String url = matcher.group(matchGroup);
                url = toCleanAbsoluteURL(referrer, url);
                if (url == null) {
                    continue;
                }
                Link link = new Link(url);
                link.setReferrer(referrer.url);
                links.add(link); 
            }
        }
    }
    
    private String toCleanAbsoluteURL(
            final Referer urlParts, final String newURL) {
        String url = StringUtils.trimToNull(newURL);
        
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

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = XMLConfigurationUtil.newXMLConfiguration(in);
        setMaxURLLength(xml.getInt("[@maxURLLength]", getMaxURLLength()));
        setCharset(xml.getString("[@charset]", getCharset()));
        setApplyToContentTypePattern(xml.getString(
                "applyToContentTypePattern", getApplyToContentTypePattern()));
        setApplyToReferencePattern(xml.getString(
                "applyToReferencePattern", getApplyToReferencePattern()));

        List<HierarchicalConfiguration> pNodes = 
                xml.configurationsAt("linkExtractionPatterns.pattern");
        if (!pNodes.isEmpty()) {
            clearPatterns();
            for (HierarchicalConfiguration pNode : pNodes) {
                int group = pNode.getInt("[@group]", 0);
                String pattern = pNode.getString("", null);
                if (StringUtils.isNotBlank(pattern)) {
                    addPattern(pattern, group);
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
            writer.writeAttributeString("charset", getCharset());

            writer.writeElementString("applyToContentTypePattern", 
                    getApplyToContentTypePattern());
            writer.writeElementString("applyToReferencePattern", 
                    getApplyToReferencePattern());

            // Tags
            writer.writeStartElement("linkExtractionPatterns");
            for (Entry<String, Integer> entry : patterns.entrySet()) {
                writer.writeStartElement("pattern");
                writer.writeAttributeInteger("group", entry.getValue());
                writer.writeCharacters(entry.getKey());
                writer.writeEndElement();
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
                .append("maxURLLength", maxURLLength)
                .append("charset", charset)
                .append("applyToContentTypePattern", applyToContentTypePattern)
                .append("applyToReferencePattern", applyToReferencePattern)
                .append("linkExtractionPatterns", patterns)
                .toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof RegexLinkExtractor)) {
            return false;
        }
        
        RegexLinkExtractor castOther = (RegexLinkExtractor) other;
        return new EqualsBuilder()
                .append(maxURLLength, castOther.maxURLLength)
                .append(charset, castOther.charset)
                .append(applyToContentTypePattern, 
                        castOther.applyToContentTypePattern)
                .append(applyToReferencePattern, 
                        castOther.applyToReferencePattern)
                .append(patterns, castOther.patterns)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(maxURLLength)
                .append(charset)
                .append(applyToContentTypePattern)
                .append(applyToReferencePattern)
                .append(patterns)
                .toHashCode();
    }
}
