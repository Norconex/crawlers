/* Copyright 2017-2020 Norconex Inc.
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
import java.io.Reader;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.link.AbstractTextLinkExtractor;
import com.norconex.collector.http.link.ILinkExtractor;
import com.norconex.collector.http.link.Link;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.CommonRestrictions;
import com.norconex.importer.handler.HandlerDoc;

/**
 * <p>
 * Link extractor for extracting links out of
 * <a href="https://en.wikipedia.org/wiki/RSS">RSS</a> and
 * <a href="https://en.wikipedia.org/wiki/Atom_(standard)">Atom</a> XML feeds.
 * It extracts the content of &lt;link&gt; tags.  If you need more complex
 * extraction, consider using {@link RegexLinkExtractor} or creating your own
 * {@link ILinkExtractor} implementation.
 * </p>
 *
 * <h3>Applicable documents</h3>
 * <p>
 * By default, this extractor only will be applied on documents matching
 * one of these content types:
 * </p>
 *
 * {@nx.include com.norconex.importer.handler.CommonRestrictions#xmlFeedContentTypes}
 *
 * <h3>Referrer data</h3>
 * <p>
 * The following referrer information is stored as metadata in each document
 * represented by the extracted URLs:
 * </p>
 * <ul>
 *   <li><b>Referrer reference:</b> The reference (URL) of the page where the
 *   link to a document was found.  Metadata value is
 *   {@link HttpDocMetadata#REFERRER_REFERENCE}.</li>
 * </ul>
 *
 * {@nx.xml.usage
 * <extractor class="com.norconex.collector.http.link.impl.XMLFeedLinkExtractor">
 *   {@nx.include com.norconex.collector.http.link.AbstractTextLinkExtractor@nx.xml.usage}
 * </extractor>
 * }
 *
 * {@nx.xml.example
 * <extractor class="com.norconex.collector.http.link.impl.XMLFeedLinkExtractor">
 *   <restrictTo field="document.reference" method="regex">.*rss$</restrictTo>
 * </extractor>
 * }
 * <p>
 * The above example specifies this extractor should only apply on documents
 * that have their URL ending with "rss" (in addition to the default
 * content types supported).
 * </p>
 *
 * @author Pascal Essiembre
 * @since 2.7.0
 */
@SuppressWarnings("javadoc")
public class XMLFeedLinkExtractor extends AbstractTextLinkExtractor {

    public XMLFeedLinkExtractor() {
        super();
        // default content type this extractor applies to
        setRestrictions(CommonRestrictions.xmlFeedContentTypes(
                DocMetadata.CONTENT_TYPE));
    }

    @Override
    public void extractTextLinks(
            Set<Link> links, HandlerDoc doc, Reader reader) throws IOException {
        try {
            XMLReader xmlReader = XMLUtil.createXMLReader();
            FeedHandler handler = new FeedHandler(doc.getReference(), links);
            xmlReader.setContentHandler(handler);
            xmlReader.setErrorHandler(handler);
            xmlReader.parse(new InputSource(reader));
        } catch (SAXException e) {
            throw new CollectorException(
                    "Could not parse XML Feed: " + doc.getReference(), e);
        }
    }

    @Override
    protected void loadTextLinkExtractorFromXML(XML xml) {
        //NOOP
    }

    @Override
    protected void saveTextLinkExtractorToXML(XML xml) {
        //NOOP
    }

    private class FeedHandler extends DefaultHandler {
        private final String referer;
        private final Set<Link> links;
        private boolean isInLink = false;
        private String stringLink="";
        public FeedHandler(String referer, Set<Link> links) {
            super();
            this.referer = referer;
            this.links = links;
        }
        @Override
        public void startElement(String uri, String localName, String qName,
                Attributes attributes) throws SAXException {
            if ("link".equalsIgnoreCase(localName)) {
                isInLink = true;
                String href = attributes.getValue("href");
                if (StringUtils.isNotBlank(href)) {
                    Link link = new Link(href);
                    link.setReferrer(referer);
                    links.add(link);
                }
            }
        }
        @Override
        public void characters(
                char[] chars, int start, int len) throws SAXException {
        	 if (isInLink && len > 0) {
          	   stringLink = stringLink+new String(chars, start, len);

             }
        }
        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
        	if ("link".equals(localName)){
     		   if(stringLink.length() > 0) {
     			   Link link = new Link(stringLink);
     			   link.setReferrer(referer);
     			   links.add(link);
     			   stringLink="";
     		   }
     		   isInLink = false;

     	   }
        }
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
