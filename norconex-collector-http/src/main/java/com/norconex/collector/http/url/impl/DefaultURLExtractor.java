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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.url.IURLExtractor;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.file.ContentType;

/**
 * Default implementation of {@link IURLExtractor}.  
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;urlExtractor class="com.norconex.collector.http.url.impl.DefaultURLExtractor"&gt;
 *      &lt;maxURLLength&gt;
 *          (Optional maximum URL length.  Longer URLs won't be extracted.
 *           Default is 2048.)
 *      &lt;/maxURLLength&gt;
 *  &lt;/urlExtractor&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class DefaultURLExtractor implements IURLExtractor, IXMLConfigurable {

    private static final long serialVersionUID = 4130729871145622411L;
    private static final Logger LOG = LogManager.getLogger(
            DefaultURLExtractor.class);

    /** Default maximum length a URL can have. */
    public static final int DEFAULT_MAX_URL_LENGTH = 2048;
    private static final int LOGGING_MAX_URL_LENGTH = 200;
    
    private static final Pattern URL_PATTERN = Pattern.compile(
           "(\\W|^)(url|data-url|href|src)(\\s*=\\s*)([\"']{0,1})(.*?)([\"'>])",
           Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    
    private static final Pattern META_REFRESH_PATTERN = Pattern.compile(
            "<\\s*meta\\s.*?http-equiv\\s*=\\s*[\"']refresh[\"']",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    
    private static final int URL_PATTERN_GROUP_URL = 5;
    private static final int URL_PATTERN_GROUP_ATTR_NAME = 2;

    private int maxURLLength = DEFAULT_MAX_URL_LENGTH;
    
    @Override
    public Set<String> extractURLs(
            Reader document, String documentUrl, ContentType contentType)
            throws IOException {
        
        // Do not extract if non-HTML
        if (!ContentType.HTML.equals(contentType)) {
            return null;
        }
        
        UrlParts urlParts = new UrlParts(documentUrl);
        
        //TODO HOW TO HANDLE <BASE>????? Is it handled by Tika???

        if (LOG.isDebugEnabled()) {
            LOG.debug("DOCUMENT URL ----> " + documentUrl);
            LOG.debug("  BASE RELATIVE -> " + urlParts.relativeBase);
            LOG.debug("  BASE ABSOLUTE -> " + urlParts.absoluteBase);
        }

        Set<String> urls = new HashSet<String>();
        
        BufferedReader reader = new BufferedReader(document);
        String line;
        while ((line = reader.readLine()) != null)   {
            Matcher matcher = URL_PATTERN.matcher(line);
            while (matcher.find()) {
                String attrName = matcher.group(URL_PATTERN_GROUP_ATTR_NAME);
                String url = matcher.group(URL_PATTERN_GROUP_URL);
                if (!isValidURL(url, attrName, line)) {
                    continue;
                }
                url = extractURL(urlParts, url);
                if (url == null) {
                    continue;
                }
                if (url.length() > maxURLLength) {
                    LOG.warn("URL length (" + url.length() + ") exeeding "
                           + "maximum length allowed (" + maxURLLength
                           + ") to be extracted. URL (showing first 200 "
                           + "chars): " + StringUtils.substring(
                                   url, 0, LOGGING_MAX_URL_LENGTH) + "...");
                } else {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    private boolean isValidURL(String url, String attributeName, String line) {
        if (StringUtils.startsWithIgnoreCase(url, "mailto:")) {
            return false;
        }
        if (StringUtils.startsWithIgnoreCase(url, "javascript:")) {
            return false;
        }
        if (attributeName != null && attributeName.equalsIgnoreCase("url")
                && !META_REFRESH_PATTERN.matcher(line).find()) {
            return false;
        }
        return true;
    }
    
    private String extractURL(final UrlParts urlParts, final String rawURL) {
        if (rawURL == null) {
            return null;
        }
        String url = rawURL;
        if (url.startsWith("//")) {
            // this is URL relative to protocol
            url = urlParts.protocol 
                    + StringUtils.substringAfter(url, "//");
        } else if (url.startsWith("/")) {
            // this is a URL relative to domain name
            url = urlParts.absoluteBase + url;
        } else if (url.startsWith("?") || url.startsWith("#")) {
            // this is a relative url and should have the full page base
            url = urlParts.documentBase + url;
        } else if (!url.contains("://")) {
            if (urlParts.relativeBase.endsWith("/")) {
                // This is a URL relative to the last URL segment
                url = urlParts.relativeBase + url;
            } else {
                url = urlParts.relativeBase + "/" + url;
            }
        }
        //TODO have configurable whether to strip anchors.
        url = StringUtils.substringBefore(url, "#");
        return url;
    }

    /**
     * Gets the maximum supported URL length.
     * @return maximum URL length
     */
    public int getMaxURLLength() {
        return maxURLLength;
    }
    /**
     * Sets the maximum supported URL length.
     * @param maxURLLength maximum URL length
     */
    public void setMaxURLLength(int maxURLLength) {
        this.maxURLLength = maxURLLength;
    }

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
        setMaxURLLength(xml.getInt("maxURLLength", DEFAULT_MAX_URL_LENGTH));
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("urlExtractor");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeStartElement("maxURLLength");
            writer.writeCharacters(Integer.toString(maxURLLength));
            writer.writeEndElement();
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }       
    }
    
    private class UrlParts {
        private final String protocol;
        private final String path;
        private final String relativeBase;
        private final String absoluteBase;
        private final String documentBase;
        public UrlParts(String documentUrl) {
            super();
            // URL Protocol/scheme, up to double slash (included)
            protocol = documentUrl.replaceFirst("(.*?://)(.*)", "$1");

            // URL Path (anything after double slash)
            path = documentUrl.replaceFirst("(.*?://)(.*)", "$2");
            
            // URL Relative Base: truncate to last / before a ? or #
            String relBase = path.replaceFirst(
                    "(.*?)([\\?\\#])(.*)", "$1");
            relativeBase = protocol +  relBase.replaceFirst("(.*/)(.*)", "$1");

            // URL Absolute Base: truncate to first / if present, after protocol
            absoluteBase = protocol + path.replaceFirst("(.*?)(/.*)", "$1");
            
            // URL Document Base: truncate from first ? or # 
            documentBase = 
                    protocol + path.replaceFirst("(.*?)([\\?\\#])(.*)", "$1");
        }
    }
}
