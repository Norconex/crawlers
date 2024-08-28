/* Copyright 2010-2024 Norconex Inc.
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

import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.DefaultHtmlMapper;
import org.apache.tika.parser.html.HtmlMapper;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.Link;
import org.apache.tika.sax.LinkContentHandler;
import org.xml.sax.SAXException;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.web.doc.operations.link.LinkExtractor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Implementation of {@link LinkExtractor} using
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
 * <extractor class="com.norconex.crawler.web.doc.operations.link.impl.TikaLinkExtractor"
 *     ignoreNofollow="[false|true]" >
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * </extractor>
 * }
 * @see HtmlLinkExtractor
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class TikaLinkExtractor
        implements LinkExtractor, Configurable<TikaLinkExtractorConfig> {

    private static final HtmlMapper fixedHtmlMapper =
            new FixedHtmlParserMapper();

    @Getter
    private final TikaLinkExtractorConfig configuration =
            new TikaLinkExtractorConfig();

    @Override
    public Set<com.norconex.crawler.web.doc.operations.link.Link> extractLinks(
            CrawlDoc doc
    )
            throws IOException {

        // only proceed if we are dealing with a supported content type
        if (!configuration.getContentTypeMatcher().matches(
                doc.getDocContext().getContentType().toString()
        )) {
            return Set.of();
        }

        var refererUrl = doc.getReference();
        Set<com.norconex.crawler.web.doc.operations.link.Link> nxLinks =
                new HashSet<>();
        if (configuration.getFieldMatcher().isSet()) {
            // Fields
            var values = doc.getMetadata()
                    .matchKeys(configuration.getFieldMatcher())
                    .valueList();
            for (String val : values) {
                extractTikaLinks(
                        nxLinks,
                        new ByteArrayInputStream(val.getBytes()), refererUrl
                );
            }
        } else {
            // Body
            extractTikaLinks(nxLinks, doc.getInputStream(), doc.getReference());
        }
        return nxLinks;
    }

    private void extractTikaLinks(
            Set<com.norconex.crawler.web.doc.operations.link.Link> nxLinks,
            InputStream is,
            String referrerUrl
    ) throws IOException {
        var linkHandler = new LinkContentHandler();
        var metadata = new Metadata();
        var parseContext = new ParseContext();
        parseContext.set(HtmlMapper.class, fixedHtmlMapper);

        var parser = new HtmlParser();
        try (is) {
            parser.parse(is, linkHandler, metadata, parseContext);
            var tikaLinks = linkHandler.getLinks();
            tikaLinks.forEach(
                    tikaLink -> toNxLink(referrerUrl, tikaLink)
                            .ifPresent(nxLinks::add)
            );

            //grab refresh URL from metadata (if present)
            Optional.ofNullable(
                    trimToNull(getCaseInsensitive(metadata, "refresh"))
            )
                    .map(LinkUtil::extractHttpEquivRefreshContentUrl)
                    .map(
                            url -> trimToNull(
                                    HttpURL.toAbsolute(referrerUrl, url)
                            )
                    )
                    .map(url -> {
                        var nxLink =
                                new com.norconex.crawler.web.doc.operations.link.Link(
                                        url
                                );
                        nxLink.setReferrer(referrerUrl);
                        return nxLink;
                    })
                    .ifPresent(nxLinks::add);
        } catch (TikaException | SAXException e) {
            throw new IOException(
                    "Could not parse to extract URLs: " + referrerUrl, e
            );
        }
    }

    private Optional<
            com.norconex.crawler.web.doc.operations.link.Link> toNxLink(
                    String referrerUrl, Link tikaLink
            ) {
        if (!configuration.isIgnoreNofollow()
                && "nofollow".equalsIgnoreCase(
                        StringUtils.trim(tikaLink.getRel())
                )) {
            return Optional.empty();
        }
        var extractedURL = tikaLink.getUri();
        if (StringUtils.isBlank(extractedURL)) {
            return Optional.empty();
        }
        if (extractedURL.startsWith("?") || extractedURL.startsWith("#")) {
            extractedURL = referrerUrl + extractedURL;
        } else {
            extractedURL = HttpURL.toAbsolute(referrerUrl, extractedURL);
        }
        if (StringUtils.isNotBlank(extractedURL)) {
            var nxLink = new com.norconex.crawler.web.doc.operations.link.Link(
                    extractedURL
            );
            nxLink.setReferrer(referrerUrl);

            if (!configuration.isIgnoreLinkData()) {
                tikaMetaToNxMeta(nxLink, tikaLink);
            }
            //System.err.println("EXTRACTED LINK: " + nxLink);
            return Optional.of(nxLink);
        }
        return Optional.empty();
    }

    private void tikaMetaToNxMeta(
            com.norconex.crawler.web.doc.operations.link.Link nxLink,
            Link tikaLink
    ) {
        var linkMeta = nxLink.getMetadata();
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
    }

    private String getCaseInsensitive(Metadata metadata, String key) {
        for (String name : metadata.names()) {
            if (StringUtils.equalsIgnoreCase(name, key)) {
                return metadata.get(name);
            }
        }
        return null;
    }

    // Custom HTML Mapper that adds "title" to the supported anchor
    // attributes, in order to be able to extract the title out of
    // anchors when keepReferrerData is true.
    private static class FixedHtmlParserMapper extends DefaultHtmlMapper {
        @Override
        public String mapSafeAttribute(
                String elementName, String attributeName
        ) {
            if ("a".equals(elementName) && "title".equals(attributeName)) {
                return "title";
            }
            return super.mapSafeAttribute(elementName, attributeName);
        }
    }
}
