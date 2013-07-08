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

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.sitemap.ISitemapsResolver;
import com.norconex.collector.http.sitemap.SitemapURLStore;
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
 * By default basic limitations imposed from the sitemap specifications are
 * respected.  Setting lenient to <code>true</code> changes that.  For 
 * example, sitemap.xml files defined in a sub-directory applies only
 * to URLs found in that sub-directory or its children.  Being lenient 
 * no longer honors that restriction.
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

    @Override
    public void resolveSitemaps(DefaultHttpClient httpClient, String urlRoot,
            String[] robotsTxtLocations, SitemapURLStore sitemapURLStore) {

        final Set<String> resolvedLocations = new HashSet<String>();

        Set<String> uniqueLocations = 
                combineLocations(robotsTxtLocations, urlRoot);
        for (String location : uniqueLocations) {
            resolveLocation(location, httpClient, sitemapURLStore, 
                    resolvedLocations);
        }
    }

    public String[] getSitemapLocations() {
        return sitemapLocations;
    }
    public void setSitemapLocations(String... sitemapLocations) {
        this.sitemapLocations = sitemapLocations;
    }
    


    @Override
    public void loadFromXML(Reader in) throws IOException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        // TODO Auto-generated method stub
        
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
                parseLocation(is, httpClient, sitemapURLStore, 
                        resolvedLocations);
                IOUtils.closeQuietly(is);
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
            throw new HttpCollectorException(e);
        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }  
        
        
    }
    
    private void parseLocation(InputStream is, DefaultHttpClient httpClient,
            SitemapURLStore sitemapURLStore, Set<String> resolvedLocations)
                    throws XMLStreamException {

        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        XMLEventReader eventReader = inputFactory.createXMLEventReader(is);
        while (eventReader.hasNext()) {
            XMLEvent event = eventReader.nextEvent();

            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                if (isTag("sitemapindex", startElement)) {
                    parseSitemapIndex(eventReader, httpClient, sitemapURLStore,
                            resolvedLocations);
                } else if (isTag("urlset", startElement)) {
                    parseSitemap(eventReader, httpClient, sitemapURLStore,
                            resolvedLocations);
                } else {
                    LOG.error("Unsupported sitemap XML tag: "
                            + startElement.getName().getLocalPart());
                }
            }
//            // If we reach the end of an item element we add it to the list
//            if (event.isEndElement()) {
//                EndElement endElement = event.asEndElement();
//                System.out.println("End Tag:" + endElement.getName());
//                if (isTag("sitemapindex", endElement)) {
//                    System.out.println("   In </sitemapindex>");
//                }
//            }
        }
    }

    private void parseSitemapIndex(
            XMLEventReader eventReader, DefaultHttpClient httpClient,
            SitemapURLStore sitemapURLStore, Set<String> resolvedLocations)
                    throws XMLStreamException {
        
//        eventReader.
        
        System.out.println("   In <sitemapindex>");
    }
    
    private void parseSitemap(
            XMLEventReader eventReader, DefaultHttpClient httpClient,
            SitemapURLStore sitemapURLStore, Set<String> resolvedLocations)
                    throws XMLStreamException {
        System.out.println("   In <urlset>");
    }
            /*
            <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">

            <sitemap>

               <loc>http://www.example.com/sitemap1.xml.gz</loc>

               <lastmod>2004-10-01T18:23:17+00:00</lastmod>

            </sitemap>

            <sitemap>

               <loc>http://www.example.com/sitemap2.xml.gz</loc>

               <lastmod>2005-01-01</lastmod>

            </sitemap>

         </sitemapindex>

             */
            
    private boolean isTag(String tagName, StartElement element) {
        return tagName.equalsIgnoreCase(element.getName().getLocalPart());
    }
    private boolean isTag(String tagName, EndElement element) {
        return tagName.equalsIgnoreCase(element.getName().getLocalPart());
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
    

}
