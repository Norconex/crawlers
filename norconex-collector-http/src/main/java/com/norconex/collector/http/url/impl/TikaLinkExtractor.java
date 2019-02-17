/* Copyright 2010-2019 Norconex Inc.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

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

    private static final Logger LOG = LoggerFactory.getLogger(
            TikaLinkExtractor.class);

    private static final List<ContentType> DEFAULT_CONTENT_TYPES =
            Collections.unmodifiableList(Arrays.asList(
                ContentType.HTML,
                ContentType.valueOf("application/xhtml+xml"),
                ContentType.valueOf("vnd.wap.xhtml+xml"),
                ContentType.valueOf("x-asp")
            ));
    private static final Pattern META_REFRESH_PATTERN = Pattern.compile(
            "(\\W|^)(url)(\\s*=\\s*)([\"']{0,1})(.+?)([\"'>])",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int URL_PATTERN_GROUP_URL = 5;

    //TODO consider an abstract class if content type and referrer data
    // pops up again.
    private final List<ContentType> contentTypes =
            new ArrayList<>(DEFAULT_CONTENT_TYPES);
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
            is.close();
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

    public List<ContentType> getContentTypes() {
        return Collections.unmodifiableList(contentTypes);
    }
    /**
     * Sets the content types on which to perform link extraction.
     * @param contentTypes content types
     */
    public void setContentTypes(ContentType... contentTypes) {
        CollectionUtil.setAll(this.contentTypes, contentTypes);
    }
    /**
     * Sets the content types on which to perform link extraction.
     * @param contentTypes content types
     * @since 3.0.0
     */
    public void setContentTypes(List<ContentType> contentTypes) {
        CollectionUtil.setAll(this.contentTypes, contentTypes);
    }

    public boolean isIgnoreNofollow() {
        return ignoreNofollow;
    }
    public void setIgnoreNofollow(boolean ignoreNofollow) {
        this.ignoreNofollow = ignoreNofollow;
    }

    @Override
    public boolean accepts(String url, ContentType contentType) {
        return contentTypes.contains(contentType);
    }

    private String resolve(String docURL, String extractedURL) {
        try {
            URI uri = new URI(extractedURL);
            if(uri.getScheme() == null) {
                uri = new URI(docURL).resolve(extractedURL);
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            LOG.info("Could not resolve extracted URL: \"" + extractedURL
                    + "\" from document \"" + docURL + "\".");
        }
        return null;
    }

    @Override
    public void loadFromXML(XML xml) {
        setIgnoreNofollow(xml.getBoolean("@ignoreNofollow", ignoreNofollow));
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
        xml.setAttribute("ignoreNofollow", ignoreNofollow);
        xml.addDelimitedElementList("contentTypes", contentTypes);
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
