/* Copyright 2010-2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.url.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.Link;
import org.apache.tika.sax.LinkContentHandler;

import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.EqualsBuilder;

/**
 * Implementation of {@link ILinkExtractor} using 
 * <a href="http://tika.apache.org/">Apache Tika</a> to perform URL 
 * extractions from HTML documents.
 * This is an alternative to the {@link HtmlLinkExtractor}.
 * <p />
 * The configuration of content-types and keeping the referrer data is the same
 * as {@link HtmlLinkExtractor}.
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;extractor class="com.norconex.collector.http.url.impl.TikeLinkExtractor"
 *          ignoreNofollow="(false|true)" 
 *          keepReferrerData="(false|true)"&gt;
 *      &lt;contentTypes&gt;
 *          (CSV list of content types on which to perform link extraction.
 *           leave blank or remove tag to use defaults.)
 *      &lt;/contentTypes&gt;
 *  &lt;/linkExtractor&gt;  
 * </pre>
 * @author Pascal Essiembre
 * @see HtmlLinkExtractor
 */
public class TikaLinkExtractor implements ILinkExtractor, IXMLConfigurable {

    private static final Logger LOG = LogManager.getLogger(
            TikaLinkExtractor.class);

    private static final ContentType[] DEFAULT_CONTENT_TYPES = 
            new ContentType[] {
        ContentType.HTML,
        ContentType.valueOf("application/xhtml+xml"),
        ContentType.valueOf("vnd.wap.xhtml+xml"),
        ContentType.valueOf("x-asp"),
    };
    private static final Pattern META_REFRESH_PATTERN = Pattern.compile(
            "(\\W|^)(url)(\\s*=\\s*)([\"']{0,1})(.+?)([\"'>])",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int URL_PATTERN_GROUP_URL = 5;
    
    //TODO consider an abstract class if content type and referrer data
    // pops up again.
    private ContentType[] contentTypes = DEFAULT_CONTENT_TYPES;
    private boolean ignoreNofollow;
    private boolean keepReferrerData;

    @Override
    public Set<com.norconex.collector.http.url.Link> extractLinks(
            InputStream is, String url, ContentType contentType)
            throws IOException {
        LinkContentHandler linkHandler = new LinkContentHandler();
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        HtmlParser parser = new HtmlParser();
        try {
            parser.parse(is, linkHandler, metadata, parseContext);
            IOUtils.closeQuietly(is);
            List<Link> tikaLinks = linkHandler.getLinks();
            Set<com.norconex.collector.http.url.Link> nxLinks = 
                    new HashSet<>(tikaLinks.size());
            for (Link tikaLink : tikaLinks) {
                if (!isIgnoreNofollow() 
                        && "nofollow".equalsIgnoreCase(
                                StringUtils.trim(tikaLink.getRel()))) {
                    continue;
                }
                String extractedURL = tikaLink.getUri();
                if (extractedURL.startsWith("?")) {
                    extractedURL = url + extractedURL;
                } else if (extractedURL.startsWith("#")) {
                    extractedURL = url;
                } else {
                    extractedURL = resolve(url, extractedURL);
                }
                if (StringUtils.isNotBlank(extractedURL)) {
                    com.norconex.collector.http.url.Link nxLink = 
                            new com.norconex.collector.http.url.Link(
                                    extractedURL);
                    if (keepReferrerData) {
                        nxLink.setReferrer(url);
                        nxLink.setText(tikaLink.getText());
                        if (tikaLink.isAnchor()) {
                            nxLink.setTag("a.href");
                        } else if (tikaLink.isImage()) {
                            nxLink.setTag("img.src");
                        }
                        nxLink.setTitle(tikaLink.getTitle());
                    }
                    nxLinks.add(nxLink);
                }
            }

            //grab refresh URL from metadata (if present)
            String refreshURL = metadata.get("refresh");
            if (StringUtils.isNotBlank(refreshURL)) {
                Matcher matcher = META_REFRESH_PATTERN.matcher(refreshURL);
                if (matcher.find()) {
                    refreshURL = matcher.group(URL_PATTERN_GROUP_URL);
                }
                refreshURL = resolve(url, refreshURL);
                if (StringUtils.isNotBlank(refreshURL)) {
                    com.norconex.collector.http.url.Link nxLink = 
                            new com.norconex.collector.http.url.Link(
                                    refreshURL);
                    if (keepReferrerData) {
                        nxLink.setReferrer(url);
                    }
                    nxLinks.add(nxLink);
                }
            }
            return nxLinks;
        } catch (Exception e) {
            throw new IOException("Could not parse to extract URLs: " + url, e);
        }
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

    @Override
    public boolean accepts(String url, ContentType contentType) {
        if (ArrayUtils.isEmpty(contentTypes)) {
            return true;
        }
        return ArrayUtils.contains(contentTypes, contentType);
    }
    
    private String resolve(String docURL, String extractedURL) {
        try {
            URI uri = new URI(extractedURL);
            if(uri.getScheme() == null) {
                uri = new URI(docURL).resolve(extractedURL);
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            LOG.error("Could not resolve extracted URL: \"" + extractedURL
                    + "\" from document \"" + docURL + "\".");
        }
        return null;
    }
    
    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
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
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeStartElement("extractor");
            writer.writeAttribute("class", getClass().getCanonicalName());

            writer.writeAttributeBoolean("ignoreNofollow", isIgnoreNofollow());
            writer.writeAttributeBoolean(
                    "keepReferrerData", isKeepReferrerData());
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
                .append("ignoreNofollow", ignoreNofollow)
                .append("keepReferrerData", keepReferrerData).toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof TikaLinkExtractor)) {
            return false;
        }
        TikaLinkExtractor castOther = (TikaLinkExtractor) other;
        return new EqualsBuilder().append(contentTypes, castOther.contentTypes)
                .append(ignoreNofollow, castOther.ignoreNofollow)
                .append(keepReferrerData, castOther.keepReferrerData)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(contentTypes)
                .append(ignoreNofollow).append(keepReferrerData).toHashCode();
    }
}
