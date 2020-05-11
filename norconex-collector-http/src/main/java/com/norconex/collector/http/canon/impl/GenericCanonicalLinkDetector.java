/* Copyright 2015-2020 Norconex Inc.
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
package com.norconex.collector.http.canon.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.client.utils.URIUtils;

import com.norconex.collector.http.canon.ICanonicalLinkDetector;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>Generic canonical link detector. It detects links from the HTTP headers
 * as well as HTML files.  Good canonical reference documentation can be found
 * on this <a href="https://support.google.com/webmasters/answer/139066">
 * Google Webmaster Tools help page</a>.</p>
 *
 * <h3>HTTP Headers</h3>
 * <p>This detector will look for a metadata field (normally obtained
 * from the HTTP Headers) name called "Link" with a
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
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;canonicalLinkDetector
 *          class="com.norconex.collector.http.canon.impl.GenericCanonicalLinkDetector"
 *          ignore="(false|true)"&gt;
 *      &lt;contentTypes&gt;
 *          (CSV list of content types on which to perform canonical link
 *           detection. Leave blank or remove this tag to use defaults.)
 *      &lt;/contentTypes&gt;
 *  &lt;/canonicalLinkDetector&gt;
 * </pre>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following example ignores canonical link resolution.
 * </p>
 * <pre>
 *  &lt;canonicalLinkDetector ignore="true"/&gt;
 * </pre>
 *
 * @author Pascal Essiembre
 * @since 2.2.0
 */
public class GenericCanonicalLinkDetector
        implements ICanonicalLinkDetector, IXMLConfigurable {

    private static final List<ContentType> DEFAULT_CONTENT_TYPES =
            Collections.unmodifiableList(Arrays.asList(
                ContentType.HTML,
                ContentType.valueOf("application/xhtml+xml"),
                ContentType.valueOf("vnd.wap.xhtml+xml"),
                ContentType.valueOf("x-asp")
            ));

    private final List<ContentType> contentTypes =
            new ArrayList<>(DEFAULT_CONTENT_TYPES);


    public List<ContentType> getContentTypes() {
        return Collections.unmodifiableList(contentTypes);
    }
    /**
     * Sets the content types on which to perform canonical link detection.
     * @param contentTypes content types
     */
    public void setContentTypes(ContentType... contentTypes) {
        CollectionUtil.setAll(this.contentTypes, contentTypes);
    }
    /**
     * Sets the content types on which to perform canonical link detection.
     * @param contentTypes content types
     * @since 3.0.0
     */
    public void setContentTypes(List<ContentType> contentTypes) {
        CollectionUtil.setAll(this.contentTypes, contentTypes);
    }

    @Override
    public String detectFromMetadata(String reference, Properties metadata) {
        String link = StringUtils.trimToNull(metadata.getString("Link"));
        if (link != null) {
            Matcher m = Pattern.compile(
                    "<([^>]+)>\\s*;?\\s*rel\\s*=\\s*\"([^\"]+)\"").matcher(link);
            while (m.find()) {
                if ("canonical".equalsIgnoreCase(m.group(2))) {
                    return toAbsolute(reference, m.group(1));
                }
            }
        }
        return null;
    }

    private static final Pattern PATTERN_TAG =
            Pattern.compile("<\\s*(\\w+.*?)[/\\s]*>", Pattern.DOTALL);
    private static final int PATTERN_TAG_GROUP = 1;
    private static final Pattern PATTERN_NAME =
            Pattern.compile("^(\\w+)", Pattern.DOTALL);
    private static final int PATTERN_NAME_GROUP = 1;
    private static final Pattern PATTERN_REL =
            Pattern.compile("\\srel\\s*=\\s*([\"'])\\s*canonical\\s*\\1",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_URL =
            Pattern.compile("\\shref\\s*=\\s*([\"'])\\s*(.*?)\\s*\\1",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final int PATTERN_URL_GROUP = 2;

    @Override
    public String detectFromContent(
            String reference, InputStream is, ContentType contentType)
                    throws IOException {

        List<ContentType> cTypes = contentTypes;
        if (cTypes.isEmpty()) {
            cTypes = DEFAULT_CONTENT_TYPES;
        }

        // Do not extract if not a supported content type
        if (!cTypes.contains(contentType)) {
            return null;
        }
        try (   InputStreamReader isr = new InputStreamReader(is);
                TextReader r = new TextReader(
                        isr, DataUnit.KB.toBytes(16).intValue())) {
            String text = null;
            while ((text = r.readText()) != null) {
                Matcher matcher = PATTERN_TAG.matcher(text);
                while (matcher.find()) {
                    String tag = matcher.group(PATTERN_TAG_GROUP);
                    Matcher nameMatcher = PATTERN_NAME.matcher(tag);
                    nameMatcher.find();
                    String name = nameMatcher.group(
                            PATTERN_NAME_GROUP).toLowerCase();
                    if ("link".equalsIgnoreCase(name)
                            && PATTERN_REL.matcher(tag).find()) {
                        Matcher urlMatcher = PATTERN_URL.matcher(tag);
                        if (urlMatcher.find()) {
                            return toAbsolute(reference,
                                    urlMatcher.group(PATTERN_URL_GROUP));
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
    public void loadFromXML(XML xml) {
        setContentTypes(xml.getDelimitedList(
                "contentTypes", ContentType.class, contentTypes));
//        ContentType[] cts = ContentType.valuesOf(StringUtils.split(
//                StringUtils.trimToNull(xml.getString("contentTypes")), ", "));
//        if (!ArrayUtils.isEmpty(cts)) {
//            setContentTypes(cts);
//        }
    }

    @Override
    public void saveToXML(XML xml) {
        xml.addDelimitedElementList("contentTypes", contentTypes);
//        // Content Types
//        if (!ArrayUtils.isEmpty(getContentTypes())) {
//            xml.addElement("contentTypes",
//                    StringUtils.join(getContentTypes(), ','));
//        }
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
        return new ReflectionToStringBuilder(this,
                ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
