/* Copyright 2015 Norconex Inc.
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
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.client.utils.URIUtils;

import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.url.ICanonicalLinkDetector;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * <p>Generic canonical link detector. It detects links from the HTTP headers
 * as well as HTML files.  Good canonical reference documentation can be found 
 * on this <a href="https://support.google.com/webmasters/answer/139066">
 * Google Webmaster Tools help page</a>.</p>
 * 
 * <h3>HTTP Headers</h3>
 * <p>This detector will look for a metadata field name called "Link" with a 
 * value following this pattern:</p>
 * <pre>
 * &lt;http://www.example.com/sample.pdf&gt; rel="canonical"
 * </pre>
 * <p>All documents will be verified for a canonical link (not just HTML).</p>
 * 
 * <h3>Document content</h3>
 * <p>This detector will look within the HTML &lt;head&gt; tags for a 
 * &lt;link&gt; tag following this pattern:</p>
 * <pre>
 * &lt;link rel="canonical" href="https://www.example.com/sample" /&gt;
 * </pre>
 * <p>Only HTML documents will be verified for a canonical link.  By default,
 * these content-types are considered HTML:</p>
 * <pre>
 * text/html, application/xhtml+xml, vnd.wap.xhtml+xml, x-asp
 * </pre>
 * <p>You can specify your own content types as long as they contain HTML
 * text.</p>
 * 
 * <h3>XML configuration usage</h3>
 * <pre>
 *  &lt;canonicalLinkDetector 
 *          class="com.norconex.collector.http.url.impl.GenericCanonicalLinkDetector"
 *          ignore="(false|true)"&gt;
 *      &lt;contentTypes&gt;
 *          (CSV list of content types on which to perform canonical link
 *           detection. Leave blank or remove this tag to use defaults.)
 *      &lt;/contentTypes&gt;
 *  &lt;/canonicalLinkDetector&gt;
 * </pre>
 * @author Pascal Essiembre
 * @since 2.2.0
 */
public class GenericCanonicalLinkDetector 
        implements ICanonicalLinkDetector, IXMLConfigurable {

    private static final ContentType[] DEFAULT_CONTENT_TYPES = 
            new ContentType[] {
        ContentType.HTML,
        ContentType.valueOf("application/xhtml+xml"),
        ContentType.valueOf("vnd.wap.xhtml+xml"),
        ContentType.valueOf("x-asp"),
    };
    
    private ContentType[] contentTypes = DEFAULT_CONTENT_TYPES;
    

    public ContentType[] getContentTypes() {
        return contentTypes;
    }
    public void setContentTypes(ContentType... contentTypes) {
        this.contentTypes = contentTypes;
    }

    @Override
    public String detectFromMetadata(String reference, HttpMetadata metadata) {
        String link = StringUtils.trimToNull(metadata.getString("Link"));
        if (link != null) {
            if (link.toLowerCase().matches(
                    ".*rel\\s*=\\s*[\"']canonical[\"'].*")) {
                link = StringUtils.substringBetween(link, "<", ">");
                return toAbsolute(reference, link);
            }
        }
        return null;
    }

    private static final Pattern PATTERN_TAG = 
            Pattern.compile("<\\s*(\\w+.*?)[/\\s]*>", Pattern.DOTALL);
    private static final Pattern PATTERN_NAME = 
            Pattern.compile("^(\\w+)", Pattern.DOTALL);
    private static final Pattern PATTERN_REL = 
            Pattern.compile("\\srel\\s*=\\s*[\"']\\s*canonical\\s*[\"']", 
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_URL = 
            Pattern.compile("\\shref\\s*=\\s*[\"']\\s*(.*?)\\s*[\"']", 
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    @Override
    public String detectFromContent(
            String reference, InputStream is, ContentType contentType)
                    throws IOException {
        
        ContentType[] cTypes = contentTypes;
        if (ArrayUtils.isEmpty(cTypes)) {
            cTypes = DEFAULT_CONTENT_TYPES;
        }

        // Do not extract if not a supported content type
        if (!ArrayUtils.contains(cTypes, contentType)) {
            return null;
        }
        try (   InputStreamReader isr = new InputStreamReader(is);
                TextReader r = new TextReader(
                        isr, (int) DataUnit.KB.toBytes(16))) {
            String text = null;
            while ((text = r.readText()) != null) {
                Matcher matcher = PATTERN_TAG.matcher(text);
                while (matcher.find()) {
                    String tag = matcher.group(1);
                    Matcher nameMatcher = PATTERN_NAME.matcher(tag);
                    nameMatcher.find();
                    String name = nameMatcher.group(1).toLowerCase();
                    if ("link".equalsIgnoreCase(name)
                            && PATTERN_REL.matcher(tag).find()) {
                        Matcher urlMatcher = PATTERN_URL.matcher(tag);
                        if (urlMatcher.find()) {
                            return toAbsolute(reference, urlMatcher.group(1));
                        }
                        return null;
                    } else if (EqualsUtil.equalsAnyIgnoreCase(
                            name, "body", "/head")) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
    
    private String toAbsolute(String pageReference, String link) {
        if (link == null) {
            return null;
        }
        if (link.matches("^https{0,1}://")) {
            return link;
        }
        return URIUtils.resolve(URI.create(pageReference), 
                StringEscapeUtils.unescapeHtml4(link)).toString();
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
        ContentType[] cts = ContentType.valuesOf(StringUtils.split(
                StringUtils.trimToNull(xml.getString("contentTypes")), ", "));
        if (!ArrayUtils.isEmpty(cts)) {
            setContentTypes(cts);
        }
    }
    
    @Override
    public void saveToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeStartElement("canonicalLinkDetector");
            writer.writeAttribute("class", getClass().getCanonicalName());

            // Content Types
            if (!ArrayUtils.isEmpty(getContentTypes())) {
                writer.writeElementString("contentTypes", 
                        StringUtils.join(getContentTypes(), ','));
            }

            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
    
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("contentTypes", contentTypes)
                .toString();
    }
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof GenericCanonicalLinkDetector)) {
            return false;
        }
        
        GenericCanonicalLinkDetector castOther = 
                (GenericCanonicalLinkDetector) other;
        return new EqualsBuilder()
                .append(contentTypes, castOther.contentTypes)
                .isEquals();
    }
    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(contentTypes).toHashCode();
    }
}
