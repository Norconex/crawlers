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
package com.norconex.collector.http.sitemap.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.collector.http.sitemap.ISitemapResolverFactory;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;

/**
 * @author Pascal Essiembre
 *
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;sitemap ignore="(false|true)" lenient="(false|true)"
 *     class="com.norconex.collector.http.sitemap.impl.StandardSitemapResolverFactory"&gt;
 *     &lt;location&gt;(optional location of sitemap.xml)&lt;/location&gt;
 *     (... repeat location tag as needed ...)
 *  &lt;/sitemap&gt;
 * </pre>
 */
public class StandardSitemapResolverFactory 
        implements ISitemapResolverFactory, IXMLConfigurable {

    private String[] sitemapLocations;
    private boolean lenient;
    
    /**
     * Constructor.
     */
    public StandardSitemapResolverFactory() {
    }

    @Override
    public ISitemapResolver createSitemapResolver(
            HttpCrawlerConfig config, boolean resume) {
        return new StandardSitemapResolver(new SitemapStore(config, resume));
    }

    public String[] getSitemapLocations() {
        return ArrayUtils.clone(sitemapLocations);
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
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
        setLenient(xml.getBoolean("[@lenient]", false));
        setSitemapLocations(xml.getList(
                "location").toArray(ArrayUtils.EMPTY_STRING_ARRAY));
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("sitemapResolverFactory");
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

    

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.append("sitemapLocations", sitemapLocations);
        builder.append("lenient", lenient);
        return builder.toString();
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof StandardSitemapResolverFactory)) {
            return false;
        }
        StandardSitemapResolverFactory castOther = 
                (StandardSitemapResolverFactory) other;
        return new EqualsBuilder()
                .append(sitemapLocations, castOther.sitemapLocations)
                .append(lenient, castOther.lenient).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(sitemapLocations).append(lenient)
                .toHashCode();
    }
    
}
