/* Copyright 2010-2015 Norconex Inc.
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
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.DefaultHtmlMapper;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.Link;
import org.apache.tika.sax.LinkContentHandler;
import org.xml.sax.SAXException;

import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * <p>Implementation of {@link ILinkExtractor} using 
 * <a href="http://tika.apache.org/">Apache Tika</a> to perform URL 
 * extractions from HTML documents.
 * This is an alternative to the {@link GenericLinkExtractor}.
 * <br><br>
 * The configuration of content-types, storing the referrer data, and ignoring 
 * "nofollow" are the same
 * as in {@link GenericLinkExtractor}.
 * </p>
 * 
 * <h3>URL Fragments</h3>
 * <p><b>Since 2.3.0</b>, this extractor preserves hashtag characters (#) found
 * in URLs and every characters after it. It relies on the implementation
 * of {@link IURLNormalizer} to strip it if need be.  Still since 2.3.0,
 * {@link GenericURLNormalizer} is now always invoked by default, and the 
 * default set of rules defined for it will remove fragments. 
 * </p>
 * 
 * <h3>XML configuration usage</h3>
 * <pre>
 *  &lt;extractor class="com.norconex.collector.http.url.impl.TikaLinkExtractor"
 *          ignoreNofollow="(false|true)" &gt;
 *      &lt;contentTypes&gt;
 *          (CSV list of content types on which to perform link extraction.
 *           leave blank or remove tag to use defaults.)
 *      &lt;/contentTypes&gt;
 *  &lt;/extractor&gt;  
 * </pre>
 * @author Pascal Essiembre
 * @see GenericLinkExtractor
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
    private static final HtmlMapper fixedHtmlMapper = new FixedHtmlParserMapper();

    public TikaLinkExtractor() {
        super();
    }
    
    @Override
    public Set<com.norconex.collector.http.url.Link> extractLinks(
            InputStream is, String url, ContentType contentType)
            throws IOException {
        LinkContentHandler linkHandler = new LinkContentHandler();
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        parseContext.set(HtmlMapper.class, fixedHtmlMapper);
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
                if (StringUtils.isBlank(extractedURL)) {
                    continue;
                } else if (extractedURL.startsWith("?")) {
                    extractedURL = url + extractedURL;
                } else if (extractedURL.startsWith("#")) {
                    extractedURL = url + extractedURL;
                } else {
                    extractedURL = resolve(url, extractedURL);
                }
                if (StringUtils.isNotBlank(extractedURL)) {
                    com.norconex.collector.http.url.Link nxLink = 
                            new com.norconex.collector.http.url.Link(
                                    extractedURL);
                    nxLink.setReferrer(url);
                    if (StringUtils.isNotBlank(tikaLink.getText())) {
                        nxLink.setText(tikaLink.getText());
                    }
                    if (tikaLink.isAnchor()) {
                        nxLink.setTag("a.href");
                    } else if (tikaLink.isImage()) {
                        nxLink.setTag("img.src");
                    }
                    if (StringUtils.isNotBlank(tikaLink.getTitle())) {
                        nxLink.setTitle(tikaLink.getTitle());
                    }
                    nxLinks.add(nxLink);
                }
            }

            //grab refresh URL from metadata (if present)
            String refreshURL = getCaseInsensitive(metadata, "refresh");
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
                    nxLink.setReferrer(url);
                    nxLinks.add(nxLink);
                }
            }
            
            return nxLinks;
        } catch (TikaException | SAXException e) {
            throw new IOException("Could not parse to extract URLs: " + url, e);
        }
    }

    private String getCaseInsensitive(Metadata metadata, String key) {
        for (String name: metadata.names()) {
            if (StringUtils.equalsIgnoreCase(name, key)) {
                return metadata.get(name);
            }
        }
        return null;
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
        XMLConfiguration xml = XMLConfigurationUtil.newXMLConfiguration(in);
        setIgnoreNofollow(xml.getBoolean(
                "[@ignoreNofollow]", isIgnoreNofollow()));
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
                .toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof TikaLinkExtractor)) {
            return false;
        }
        TikaLinkExtractor castOther = (TikaLinkExtractor) other;
        return new EqualsBuilder().append(contentTypes, castOther.contentTypes)
                .append(ignoreNofollow, castOther.ignoreNofollow)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(contentTypes)
                .append(ignoreNofollow)
                .toHashCode();
    }
    

    // Custom HTML Mapper that adds "title" to the supported anchor
    // attributes, in order to be able to extract the title out of 
    // anchors when keepReferrerData is true.
    private static class FixedHtmlParserMapper extends DefaultHtmlMapper {
        @Override
        public String mapSafeAttribute(
                String elementName, String attributeName) {
            if ("a".equals(elementName) && "title".equals(attributeName)) {
                return "title";
            }
            return super.mapSafeAttribute(elementName, attributeName);
        }
    }
}
