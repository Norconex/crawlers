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
package com.norconex.collector.http.robot.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.robot.IRobotsMetaProvider;
import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.ContentType;

/**
 * <p>Default implementation of {@link IRobotsMetaProvider}. 
 * Extracts robots information from "ROBOTS" meta tag in an HTML page
 * or "X-Robots-Tag" tag in the HTTP header.</p>
 * 
 * <p>If you specified a prefix for the HTTP headers, make sure to specify it 
 * again here or the robots meta tags will not be found.</p>
 * 
 * <p>If robots instructions are provided in both the HTML page and 
 * HTTP header, the ones in HTML page will take precedence, and the
 * ones in HTTP header will be ignored.</p>
 * 
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;robotsMeta ignore="false" 
 *     class="com.norconex.collector.http.robot.DefaultRobotsMetaProvider"&gt;
 *     &lt;headersPrefix&gt;(string prefixing headers)&lt;/headersPrefix&gt;
 *  &lt;/robotsMeta&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class DefaultRobotsMetaProvider 
        implements IRobotsMetaProvider, IXMLConfigurable {

    private static final long serialVersionUID = 5762255033770481717L;

    private static final Logger LOG = LogManager.getLogger(
            DefaultRobotsMetaProvider.class);
    private static final Pattern ROBOTS_PATTERN = Pattern.compile(
            "(<\\s*META.*?NAME\\s*=\\s*[\"']{0,1})(.+?)([\"'>])",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CONTENT_PATTERN = Pattern.compile(
            "(<\\s*META.*?CONTENT\\s*=\\s*[\"']{0,1})(.+?)([\"'>])",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HEAD_PATTERN = Pattern.compile(
            "<\\s*/\\s*HEAD\\s*>",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    
    private String headersPrefix;

    @Override
    public RobotsMeta getRobotsMeta(Reader document, String documentUrl,
           ContentType contentType, Properties httpHeaders) throws IOException {

        RobotsMeta robotsMeta = null;
        
        //--- Find in page ---
        if (isMetaSupportingContentType(contentType)) {
            BufferedReader reader = new BufferedReader(document);
            String line;
            while ((line = reader.readLine()) != null)   {
                if (isRobotMeta(line)) {
                    String content = getRobotRules(line);
                    robotsMeta = buildMeta(content);
                    if (LOG.isDebugEnabled() && robotsMeta != null) {
                        LOG.debug("Meta robots \"" + content 
                             + "\" found in HTML meta tag for: " + documentUrl);
                    }
                    break;
                } else if (isEndOfHead(line)) {
                    break;
                }
            }
            reader.close();
        }
        
        //--- Find in HTTP header ---
        if (robotsMeta == null) {
            robotsMeta = findInHeaders(httpHeaders, documentUrl);
        }

        if (LOG.isDebugEnabled() && robotsMeta == null) {
            LOG.debug("No meta robots found for: " + documentUrl);
        }
        
        return robotsMeta;
    }
    
    public String getHeadersPrefix() {
        return headersPrefix;
    }
    public void setHeadersPrefix(String headersPrefix) {
        this.headersPrefix = headersPrefix;
    }

    private boolean isMetaSupportingContentType(ContentType contentType) {
        return contentType != null && contentType.equals(ContentType.HTML);
    }
    
    private RobotsMeta findInHeaders(
            Properties httpHeaders, String documentUrl) {
        String name = "X-Robots-Tag";
        if (StringUtils.isNotBlank(headersPrefix)) {
            name = headersPrefix + name;
        }
        String content = httpHeaders.getString(name);
        RobotsMeta robotsMeta = buildMeta(content);
        if (LOG.isDebugEnabled() && robotsMeta != null) {
            LOG.debug("Meta robots \"" + content 
                    + "\" found in HTTP header for: " + documentUrl);
        }
        return robotsMeta;
    }
    
    private RobotsMeta buildMeta(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        String[] rules = StringUtils.split(content, ',');
        boolean noindex = false;
        boolean nofollow = false;
        for (String rule : rules) {
            if (rule.trim().equalsIgnoreCase("noindex")) {
                noindex = true;
            }
            if (rule.trim().equalsIgnoreCase("nofollow")) {
                nofollow = true;
            }
        }
        return new RobotsMeta(nofollow, noindex);
    }
    
    private boolean isRobotMeta(String line) {
        Matcher matcher = ROBOTS_PATTERN.matcher(line);
        if (matcher.find()) {
            String name = matcher.group(2);
            return "robots".equalsIgnoreCase(name);
        }
        return false;
    }
    private boolean isEndOfHead(String line) {
        return HEAD_PATTERN.matcher(line).matches();
    }
    private String getRobotRules(String line) {
        Matcher matcher = CONTENT_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setHeadersPrefix(xml.getString("headersPrefix"));
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("robotsMeta");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeStartElement("headersPrefix");
            if (headersPrefix != null) {
                writer.writeCharacters(headersPrefix);
            }
            writer.writeEndElement();
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
}
