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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.collector.http.url.Link;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

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
 * By default, this extractor will extract URLs only in documents having
 * their content type being one of the following: 
 * </p>
 * <pre>
 * application/rss+xml
 * application/rdf+xml 
 * application/atom+xml
 * application/xml
 * text/xml
 * </pre>
 * <p>
 * You can specify your own content types or reference restriction patterns
 * using {@link #setApplyToContentTypePattern(String)} or
 * {@link #setApplyToReferencePattern(String)}, but make sure they 
 * represent text files. When both methods are used, a document should be
 * be matched by both to be accepted.  Because "text/xml" and "application/xml"
 * are quite generic (not specific to RSS/Atom feeds), you may want to 
 * consider being more restrictive if that causes issues.
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
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;extractor class="com.norconex.collector.http.url.impl.XMLFeedLinkExtractor"&gt;
 *      &lt;applyToContentTypePattern&gt;
 *          (Regular expression matching content types this extractor 
 *           should apply to. See documentation for default.)
 *      &lt;/applyToContentTypePattern&gt;
 *      &lt;applyToReferencePattern&gt;
 *          (Regular expression matching references this extractor should
 *           apply to. Default accepts all references.)
 *      &lt;/applyToReferencePattern&gt;
 *  &lt;/extractor&gt;
 * </pre>
 * 
 * <h4>Usage example:</h4>
 * <p>
 * The following specifies this extractor should only apply on documents 
 * that have their URL ending with "rss" (in addition to the default
 * content types supported).
 * </p>
 * <pre>
 *  &lt;extractor class="com.norconex.collector.http.url.impl.XMLFeedLinkExtractor"&gt;
 *      &lt;applyToReferencePattern&gt;.*rss$&lt;/applyToReferencePattern&gt;
 *  &lt;/extractor&gt;
 * </pre>
 * 
 * @author Pascal Essiembre
 * @since 2.7.0
 */
public class XMLFeedLinkExtractor implements ILinkExtractor, IXMLConfigurable {

    public static final String DEFAULT_CONTENT_TYPE_PATTERN = 
            "application/(rss\\+|rdf\\+|atom\\+){0,1}xml|text/xml";
    
    private String applyToContentTypePattern = DEFAULT_CONTENT_TYPE_PATTERN;
    private String applyToReferencePattern;
    
    public XMLFeedLinkExtractor() {
        super();
    }

    @Override
    public Set<Link> extractLinks(InputStream input, String reference,
            ContentType contentType) throws IOException {
        Set<Link> links = new HashSet<>();
        try {
            XMLReader xmlReader = XMLReaderFactory.createXMLReader();
            FeedHandler handler = new FeedHandler(reference, links);
            xmlReader.setContentHandler(handler);
            xmlReader.setErrorHandler(handler);
            xmlReader.parse(new InputSource(input));
        } catch (SAXException e) {
            throw new CollectorException(
                    "Could not parse XML Feed: " + reference, e);
        }
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

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = XMLConfigurationUtil.newXMLConfiguration(in);
        setApplyToContentTypePattern(xml.getString(
                "applyToContentTypePattern", getApplyToContentTypePattern()));
        setApplyToReferencePattern(xml.getString(
                "applyToReferencePattern", getApplyToReferencePattern()));
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeStartElement("extractor");
            writer.writeAttribute("class", getClass().getCanonicalName());

            writer.writeElementString("applyToContentTypePattern", 
                    getApplyToContentTypePattern());
            writer.writeElementString("applyToReferencePattern", 
                    getApplyToReferencePattern());

            writer.writeEndElement();
            writer.flush();
            writer.close();
            
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
        
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
    };
    
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("applyToContentTypePattern", applyToContentTypePattern)
                .append("applyToReferencePattern", applyToReferencePattern)
                .toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof XMLFeedLinkExtractor)) {
            return false;
        }
        
        XMLFeedLinkExtractor castOther = (XMLFeedLinkExtractor) other;
        return new EqualsBuilder()
                .append(applyToContentTypePattern, 
                        castOther.applyToContentTypePattern)
                .append(applyToReferencePattern, 
                        castOther.applyToReferencePattern)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(applyToContentTypePattern)
                .append(applyToReferencePattern)
                .toHashCode();
    }
}
