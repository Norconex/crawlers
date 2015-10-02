/* Copyright 2010-2015 Norconex Inc.
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
package com.norconex.collector.http.sitemap.impl;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Objects;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.collector.http.sitemap.ISitemapResolverFactory;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * Factory used to created {@link StandardSitemapResolver} instances.
 * @author Pascal Essiembre
 *
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;sitemap ignore="(false|true)" lenient="(false|true)" 
 *        tempDir="(where to store temp files)"
 *     class="com.norconex.collector.http.sitemap.impl.StandardSitemapResolverFactory"&gt;
 *     &lt;location&gt;(optional location of sitemap.xml)&lt;/location&gt;
 *     (... repeat location tag as needed ...)
 *  &lt;/sitemap&gt;
 * </pre>
 */
public class StandardSitemapResolverFactory 
        implements ISitemapResolverFactory, IXMLConfigurable {

    private File tempDir;
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
  
        File resolvedTempDir = tempDir;
        if (resolvedTempDir == null) {
            resolvedTempDir = config.getWorkDir();
        }
        if (resolvedTempDir == null) {
            resolvedTempDir = FileUtils.getTempDirectory();
        }
        StandardSitemapResolver sr = new StandardSitemapResolver(
                resolvedTempDir, new SitemapStore(config, resume));
        sr.setLenient(lenient);
        sr.setSitemapLocations(sitemapLocations);
        return sr;
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

    /**
     * Gets the directory where sitemap files are temporary stored
     * before they are parsed.  When <code>null</code> (default), temporary
     * files are created directly under {@link HttpCrawlerConfig#getWorkDir()}.
     * the crawler working directory is also undefined, it will use the 
     * system temporary directory, as returned by 
     * {@link FileUtils#getTempDirectory()}.
     * @return directory where temporary files are written
     * @since 2.3.0
     */
    public File getTempDir() {
        return tempDir;
    }
    /**
     * Sets the temporary directory where sitemap files are temporary stored
     * before they are parsed.  
     * @param tempDir directory where temporary files are written
     * @since 2.3.0
     */
    public void setTempDir(File tempDir) {
        this.tempDir = tempDir;
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
        String tempPath = xml.getString(
                "tempDir", Objects.toString(getTempDir(), null));
        if (tempPath != null) {
            setTempDir(new File(tempPath));
        }
        setLenient(xml.getBoolean("[@lenient]", false));
        setSitemapLocations(xml.getList(
                "location").toArray(ArrayUtils.EMPTY_STRING_ARRAY));
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeStartElement("sitemapResolverFactory");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeAttribute("lenient", Boolean.toString(lenient));
            writer.writeElementString("tempDir", getTempDir().toString());
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
        builder.append("tempDir", tempDir);
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
                .append(lenient, castOther.lenient)
                .append(tempDir, castOther.tempDir)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(sitemapLocations)
                .append(lenient)
                .append(tempDir)
                .toHashCode();
    }
    
}
