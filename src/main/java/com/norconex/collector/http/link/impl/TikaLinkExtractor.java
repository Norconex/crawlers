/* Copyright 2010-2020 Norconex Inc.
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
package com.norconex.collector.http.link.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.DefaultHtmlMapper;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.Link;
import org.apache.tika.sax.LinkContentHandler;
import org.xml.sax.SAXException;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.http.link.AbstractLinkExtractor;
import com.norconex.collector.http.link.ILinkExtractor;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;

/**
 * <p>
 * Implementation of {@link ILinkExtractor} using
 * <a href="http://tika.apache.org/">Apache Tika</a> to perform URL
 * extractions from HTML documents.
 * This is an alternative to the {@link HtmlLinkExtractor}.
 * </p>
 * <p>
 * The configuration of content-types, storing the referrer data, and ignoring
 * "nofollow" and ignoring link data are the same as in
 * {@link HtmlLinkExtractor}. For link data, this parser only keeps a
 * pre-defined set of link attributes, when available (title, type,
 * uri, text, rel).
 * </p>
 *
 * {@nx.xml.usage
 * <extractor class="com.norconex.collector.http.link.impl.TikaLinkExtractor"
 *     ignoreNofollow="[false|true]" >
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * </extractor>
 * }
 * @author Pascal Essiembre
 * @see HtmlLinkExtractor
 */
@SuppressWarnings("javadoc")
public class TikaLinkExtractor extends AbstractLinkExtractor {

    private static final Pattern META_REFRESH_PATTERN = Pattern.compile(
            "(\\W|^)(url)(\\s*=\\s*)([\"']{0,1})(.+?)([\"'>])",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int URL_PATTERN_GROUP_URL = 5;

    private boolean ignoreNofollow;
    private boolean ignoreLinkData;
    private static final HtmlMapper fixedHtmlMapper = new FixedHtmlParserMapper();

    public TikaLinkExtractor() {
        super();
        // default content type this extractor applies to
        setRestrictions(CommonRestrictions.htmlContentTypes(
                DocMetadata.CONTENT_TYPE));
    }

    @Override
    public void extractLinks(Set<com.norconex.collector.http.link.Link> nxLinks,
            CrawlDoc doc) throws IOException {
        LinkContentHandler linkHandler = new LinkContentHandler();
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        parseContext.set(HtmlMapper.class, fixedHtmlMapper);

        HtmlParser parser = new HtmlParser();
        String url = doc.getReference();
        try (InputStream is = doc.getInputStream()) {
            parser.parse(is, linkHandler, metadata, parseContext);
            List<Link> tikaLinks = linkHandler.getLinks();
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
                    extractedURL = HttpURL.toAbsolute(url, extractedURL);
                }
                if (StringUtils.isNotBlank(extractedURL)) {
                    com.norconex.collector.http.link.Link nxLink =
                            new com.norconex.collector.http.link.Link(
                                    extractedURL);
                    nxLink.setReferrer(url);

                    if (!ignoreLinkData) {
                        Properties linkMeta = nxLink.getMetadata();
                        if (StringUtils.isNotBlank(tikaLink.getText())) {
                            linkMeta.set("text", tikaLink.getText());
                        }
                        if (StringUtils.isNotBlank(tikaLink.getType())) {
                            linkMeta.set("tag", tikaLink.getType());
                            if (tikaLink.isAnchor()
                                    || tikaLink.isLink()) {
                                linkMeta.set("attr", "href");
                            } else if (tikaLink.isIframe()
                                    || tikaLink.isImage()
                                    || tikaLink.isScript()) {
                                linkMeta.set("attr", "src");
                            }
                        }
                        if (StringUtils.isNotBlank(tikaLink.getTitle())) {
                            linkMeta.set("attr.title", tikaLink.getTitle());
                        }
                        if (StringUtils.isNotBlank(tikaLink.getRel())) {
                            linkMeta.set("attr.rel", tikaLink.getRel());
                        }
                        nxLinks.add(nxLink);
                    }
                }
            }

            //grab refresh URL from metadata (if present)
            String refreshURL = getCaseInsensitive(metadata, "refresh");
            if (StringUtils.isNotBlank(refreshURL)) {
                Matcher matcher = META_REFRESH_PATTERN.matcher(refreshURL);
                if (matcher.find()) {
                    refreshURL = matcher.group(URL_PATTERN_GROUP_URL);
                }
                refreshURL = HttpURL.toAbsolute(url, refreshURL);
                if (StringUtils.isNotBlank(refreshURL)) {
                    com.norconex.collector.http.link.Link nxLink =
                            new com.norconex.collector.http.link.Link(
                                    refreshURL);
                    nxLink.setReferrer(url);
                    nxLinks.add(nxLink);
                }
            }
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

    public boolean isIgnoreNofollow() {
        return ignoreNofollow;
    }
    public void setIgnoreNofollow(boolean ignoreNofollow) {
        this.ignoreNofollow = ignoreNofollow;
    }

    /**
     * Gets whether to ignore extra data associated with a link.
     * @return <code>true</code> to ignore.
     * @since 3.0.0
     */
    public boolean isIgnoreLinkData() {
        return ignoreLinkData;
    }
    /**
     * Sets whether to ignore extra data associated with a link.
     * @param ignoreLinkData <code>true</code> to ignore.
     * @since 3.0.0
     */
    public void setIgnoreLinkData(boolean ignoreLinkData) {
        this.ignoreLinkData = ignoreLinkData;
    }

    @Override
    protected void loadLinkExtractorFromXML(XML xml) {
        setIgnoreNofollow(xml.getBoolean("@ignoreNofollow", ignoreNofollow));
        setIgnoreLinkData(xml.getBoolean("@ignoreLinkData", ignoreLinkData));
    }

    @Override
    protected void saveLinkExtractorToXML(XML xml) {
        xml.setAttribute("ignoreNofollow", ignoreNofollow);
        xml.setAttribute("ignoreLinkData", ignoreLinkData);
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
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
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
