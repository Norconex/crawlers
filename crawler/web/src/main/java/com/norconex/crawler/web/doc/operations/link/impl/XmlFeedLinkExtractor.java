/* Copyright 2017-2024 Norconex Inc.
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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.xml.XmlUtil;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.doc.operations.link.Link;
import com.norconex.crawler.web.doc.operations.link.LinkExtractor;
import com.norconex.importer.handler.CommonMatchers;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * <p>
 * Link extractor for extracting links out of
 * <a href="https://en.wikipedia.org/wiki/RSS">RSS</a> and
 * <a href="https://en.wikipedia.org/wiki/Atom_(standard)">Atom</a> XML feeds.
 * It extracts the content of &lt;link&gt; tags.  If you need more complex
 * extraction, consider using {@link RegexLinkExtractor} or creating your own
 * {@link LinkExtractor} implementation.
 * </p>
 *
 * <h3>Applicable documents</h3>
 * <p>
 * By default, this extractor only will be applied on documents matching
 * one of the content types defined by
 * {@link CommonMatchers#XML_FEED_CONTENT_TYPES}.
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
 *   {@link WebDocMetadata#REFERRER_REFERENCE}.</li>
 * </ul>
 *
 * @since 2.7.0
 */
@EqualsAndHashCode
@ToString
public class XmlFeedLinkExtractor
        implements LinkExtractor, Configurable<XmlFeedLinkExtractorConfig> {

    @Getter
    private final XmlFeedLinkExtractorConfig configuration =
            new XmlFeedLinkExtractorConfig();

    @Override
    public Set<Link> extractLinks(CrawlDoc doc) throws IOException {

        // only proceed if we are dealing with a supported content type
        if (!configuration.getContentTypeMatcher().matches(
                doc.getDocContext().getContentType().toString())) {
            return Set.of();
        }

        if (!getConfiguration().getRestrictions().isEmpty()
                && !getConfiguration().getRestrictions().matches(
                        doc.getMetadata())) {
            return Collections.emptySet();
        }

        var refererUrl = doc.getReference();
        Set<Link> links = new HashSet<>();

        if (configuration.getFieldMatcher().isSet()) {
            // Fields
            var values = doc.getMetadata()
                    .matchKeys(configuration.getFieldMatcher())
                    .valueList();
            for (String val : values) {
                extractFeedLinks(links, new StringReader(val), refererUrl);
            }
        } else {
            // Body
            extractFeedLinks(
                    links,
                    new InputStreamReader(doc.getInputStream()),
                    doc.getReference());
        }
        return links;
    }

    private void extractFeedLinks(
            Set<Link> links, Reader reader, String referrerUrl)
            throws IOException {
        try (reader) {
            var xmlReader = XmlUtil.createXMLReader();
            var handler = new FeedHandler(referrerUrl, links);
            xmlReader.setContentHandler(handler);
            xmlReader.setErrorHandler(handler);
            xmlReader.parse(new InputSource(reader));
        } catch (SAXException e) {
            throw new CrawlerException(
                    "Could not parse XML Feed: " + referrerUrl, e);
        }
    }

    private static class FeedHandler extends DefaultHandler {
        private final String referer;
        private final Set<Link> links;
        private boolean isInLink = false;
        private String stringLink = "";

        public FeedHandler(String referer, Set<Link> links) {
            this.referer = referer;
            this.links = links;
        }

        @Override
        public void startElement(
                String uri, String localName, String qName,
                Attributes attributes) throws SAXException {
            if ("link".equalsIgnoreCase(localName)) {
                isInLink = true;
                var href = attributes.getValue("href");
                if (StringUtils.isNotBlank(href)) {
                    var link = new Link(href);
                    link.setReferrer(referer);
                    links.add(link);
                }
            }
        }

        @Override
        public void characters(
                char[] chars, int start, int len) throws SAXException {
            if (isInLink && len > 0) {
                stringLink = stringLink + new String(chars, start, len);

            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            if ("link".equals(localName)) {
                if (stringLink.length() > 0) {
                    var link = new Link(stringLink);
                    link.setReferrer(referer);
                    links.add(link);
                    stringLink = "";
                }
                isInLink = false;

            }
        }
    }
}
