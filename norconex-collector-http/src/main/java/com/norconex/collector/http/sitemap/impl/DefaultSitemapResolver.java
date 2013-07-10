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
package com.norconex.collector.http.sitemap.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;

import com.norconex.collector.http.crawler.BaseURL;
import com.norconex.collector.http.sitemap.ISitemapsResolver;
import com.norconex.collector.http.sitemap.SitemapURLStore;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;

/**
 * <p>
 * Default implementation of {@link ISitemapsResolver}.  For any given URL
 * this class will look in three different places to locate sitemaps:
 * </p>
 * <ul>
 *   <li>Sitemap locations explicitly provided via configuration (or setter
 *       method on this class).</li>
 *   <li>The root-level of a URL (e.g. http://example.com/sitemap.xml)</li>
 *   <li>Any sitemaps defined in robots.txt
 *       (automatically passed as arguments to this class if robots.txt are
 *        not ignored)</li>
 * </ul>
 * <p>
 * The Sitemap specifications dictates that a sitemap.xml file defined
 * in a sub-directory applies only to URLs found in that sub-directory and 
 * its children.   This behavior is respected by default.  Setting lenient 
 * to <code>true</code> no longer honors this restriction.
 * </p>
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;sitemap ignore="false" lenient="false"
 *     class="com.norconex.collector.http.sitemap.impl.DefaultSitemapResolver"&gt;
 *     &lt;location&gt;(optional location of sitemap.xml)&lt;/location&gt;
 *     (... repeat location tag as needed ...)
 *  &lt;/sitemap&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class DefaultSitemapResolver 
        implements ISitemapsResolver, IXMLConfigurable {

    private static final long serialVersionUID = 4047819847150159618L;

    private static final Logger LOG = LogManager.getLogger(
            DefaultSitemapResolver.class);
    
    private String[] sitemapLocations;
    private boolean lenient;

    

    @Override
    public void resolveSitemaps(DefaultHttpClient httpClient, String urlRoot,
            String[] robotsTxtLocations, SitemapURLStore sitemapURLStore) {

        final Set<String> resolvedLocations = new HashSet<String>();

        Set<String> uniqueLocations = 
                combineLocations(robotsTxtLocations, urlRoot);
        for (String location : uniqueLocations) {
            resolveLocation(
                    location, httpClient, sitemapURLStore, resolvedLocations);
        }
    }

    public String[] getSitemapLocations() {
        return sitemapLocations;
    }
    public void setSitemapLocations(String... sitemapLocations) {
        this.sitemapLocations = sitemapLocations;
    }
    public boolean isLenient() {
        return lenient;
    }
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setLenient(xml.getBoolean("[@lenient]", false));
        setSitemapLocations(xml.getList(
                "location").toArray(ArrayUtils.EMPTY_STRING_ARRAY));
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("sitemap");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeAttribute("lenient", Boolean.toString(lenient));
            if (sitemapLocations != null) {
                for (String location : sitemapLocations) {
                    writer.writeStartElement("location");
                    writer.writeCharacters(location);
                    writer.writeEndElement();
                }
            }
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }

    private void resolveLocation(String location, DefaultHttpClient httpClient,
            SitemapURLStore sitemapURLStore, Set<String> resolvedLocations) {

        if (resolvedLocations.contains(location)) {
            return;
        }
        
        HttpGet method = null;
        try {
            method = new HttpGet(location);
            
            // Execute the method.
            HttpResponse response = httpClient.execute(method);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                LOG.info("Resolving sitemap: " + location);
                InputStream is = response.getEntity().getContent();
                if ("application/x-gzip".equals(
                        response.getFirstHeader("Content-Type").getValue())) {
                    is = new GZIPInputStream(is);
                }
                parseLocation(is, httpClient, sitemapURLStore, 
                        resolvedLocations, location);
                IOUtils.closeQuietly(is);
                LOG.info("         Resolved: " + location);
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                LOG.debug("No sitemap found : " + location);
            } else {
                LOG.error("Could not obtain sitemap: " + location
                        + ".  Expected status code " + HttpStatus.SC_OK
                        + ", but got " + statusCode);
            }
        } catch (Exception e) {
            LOG.error("Cannot fetch sitemap: " + location
                    + " (" + e.getMessage() + ")");
        } finally {
            resolvedLocations.add(location);
            if (method != null) {
                method.releaseConnection();
            }
        }  
    }
    
    private void parseLocation(InputStream is, DefaultHttpClient httpClient,
            SitemapURLStore sitemapURLStore, Set<String> resolvedLocations,
            String location) throws XMLStreamException {

        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
        XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(is);
        BaseURL baseURL = null;
        boolean sitemapIndex = false;
        boolean loc = false;
        boolean lastmod = false;
        boolean changefreq = false;
        boolean priority = false;
        
        String locationDir = StringUtils.substringBeforeLast(location, "/");
        int event = xmlReader.getEventType();
        while(true){
            switch(event) {
            case XMLStreamConstants.START_ELEMENT:
                String tag = xmlReader.getLocalName();
                if("sitemap".equalsIgnoreCase(tag)) {
                    sitemapIndex = true;
                } else if("url".equalsIgnoreCase(tag)){
                    baseURL = new BaseURL("", 0);
                } else if("loc".equalsIgnoreCase(tag)){
                    loc = true;
                } else if("lastmod".equalsIgnoreCase(tag)){
                    lastmod = true;
                } else if("changefreq".equalsIgnoreCase(tag)){
                    changefreq = true;
                } else if("priority".equalsIgnoreCase(tag)){
                    priority = true;
                }
                break;
            case XMLStreamConstants.CHARACTERS:
                String value = xmlReader.getText();
                if (sitemapIndex && loc) {
                    resolveLocation(value, httpClient, 
                            sitemapURLStore, resolvedLocations);
                    loc = false;
                } else if (baseURL != null) {
                    if (loc) {
                        baseURL.setUrl(value);
                        loc = false;
                    } else if (lastmod) {
                        try {
                            baseURL.setSitemapLastMod(DateTime.parse(value));
                        } catch (Exception e) {
                            LOG.info("Invalid sitemap date: " + value);
                        }
                        lastmod = false;
                    } else if (changefreq) {
                        baseURL.setSitemapChangeFreq(value);
                        changefreq = false;
                    } else if (priority) {
                        try {
                            baseURL.setSitemapPriority(Float.parseFloat(value));
                        } catch (NumberFormatException e) {
                            LOG.info("Invalid sitemap priority: " + value);
                        }
                        priority = false;
                    }
                } 
                break;
            case XMLStreamConstants.END_ELEMENT:
                tag = xmlReader.getLocalName();
                if ("sitemap".equalsIgnoreCase(tag)) {
                    sitemapIndex = false;
                } else if("url".equalsIgnoreCase(tag)
                        && baseURL.getUrl() != null){
                    if (lenient || baseURL.getUrl().startsWith(locationDir)) { 
                        sitemapURLStore.add(baseURL);
                    } else if (LOG.isDebugEnabled()) {
                        LOG.debug("Sitemap URL invalid for location directory."
                                + " URL:" + baseURL.getUrl()
                                + " Location directory: " + locationDir);
                    }
                    baseURL = null;
                }
                break;
            }
            if (!xmlReader.hasNext()) {
                break;
            }
            event = xmlReader.next();
        }
    }

    private Set<String> combineLocations(
            String[] robotsTxtLocations, String urlRoot) {
        Set<String> uniqueLocations = new HashSet<String>();
        uniqueLocations.add(urlRoot + "/sitemap_index.xml");
        uniqueLocations.add(urlRoot + "/sitemap.xml");
        if (ArrayUtils.isNotEmpty(robotsTxtLocations)) {
            uniqueLocations.addAll(Arrays.asList(robotsTxtLocations));
        }
        if (ArrayUtils.isNotEmpty(sitemapLocations)) {
            uniqueLocations.addAll(Arrays.asList(sitemapLocations));
        }
        return uniqueLocations;
    }
    
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof DefaultSitemapResolver))
            return false;
        DefaultSitemapResolver castOther = (DefaultSitemapResolver) other;
        return new EqualsBuilder()
                .append(sitemapLocations, castOther.sitemapLocations)
                .append(lenient, castOther.lenient).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(sitemapLocations).append(lenient)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("sitemapLocations", sitemapLocations)
                .append("lenient", lenient).toString();
    }

    
}
