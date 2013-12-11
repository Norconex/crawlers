/* Copyright 2010-2013 Norconex Inc.
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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.Link;
import org.apache.tika.sax.LinkContentHandler;

import com.norconex.collector.http.url.IURLExtractor;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.importer.ContentType;

/**
 * Implementation of {@link IURLExtractor} using 
 * <a href="http://tika.apache.org/">Apache Tika</a> to perform URL 
 * extractions from HTML documents.
 * This is an alternative to the {@link DefaultURLExtractor}.
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;urlExtractor class="com.norconex.collector.http.url.impl.TikeURLExtractor" /&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class TikaURLExtractor implements IURLExtractor, IXMLConfigurable {

    private static final long serialVersionUID = -1079980784629581346L;
    private static final Logger LOG = LogManager.getLogger(
            TikaURLExtractor.class);
    
    private static final Pattern META_REFRESH_PATTERN = Pattern.compile(
            "(\\W|^)(url)(\\s*=\\s*)([\"']{0,1})(.+?)([\"'>])",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final int URL_PATTERN_GROUP_URL = 5;
    
    @Override
    public Set<String> extractURLs(Reader document, String url,
            ContentType contentType) throws IOException {

        InputStream is = new ReaderInputStream(document);
        LinkContentHandler linkHandler = new LinkContentHandler();
        Metadata metadata = new Metadata();
        ParseContext parseContext = new ParseContext();
        HtmlParser parser = new HtmlParser();
        try {
            parser.parse(is, linkHandler, metadata, parseContext);
            IOUtils.closeQuietly(is);
            List<Link> links = linkHandler.getLinks();
            Set<String> urls = new HashSet<String>(links.size());
            for (Link link : links) {
                String extractedURL = link.getUri();
                if (extractedURL.startsWith("?")) {
                    extractedURL = url + extractedURL;
                } else if (extractedURL.startsWith("#")) {
                    extractedURL = url;
                } else {
                    extractedURL = resolve(url, extractedURL);
                }
                if (StringUtils.isNotBlank(extractedURL)) {
                    urls.add(extractedURL);
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
                    urls.add(refreshURL);
                }
            }
            return urls;
        } catch (Exception e) {
            throw new IOException("Could not parse to extract URLs: " + url, e);
        }
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
    public void loadFromXML(Reader in) throws IOException {
        // nothing to load
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("urlExtractor");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }

}
