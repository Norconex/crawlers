/**
 * 
 */
package com.norconex.collector.http.sitemap.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ArrayUtils;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.collector.http.sitemap.ISitemapResolverFactory;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;

/**
 * @author Pascal Essiembre
 *
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;sitemapResolverFactory ignore="(false|true)" lenient="(false|true)"
 *     class="com.norconex.collector.http.sitemap.impl.DefaultSitemapResolverFactory"&gt;
 *     &lt;location&gt;(optional location of sitemap.xml)&lt;/location&gt;
 *     (... repeat location tag as needed ...)
 *  &lt;/sitemapResolverFactory&gt;
 * </pre>
 */
public class DefaultSitemapResolverFactory 
        implements ISitemapResolverFactory, IXMLConfigurable {

    private static final long serialVersionUID = 7647490140299818323L;

    private String[] sitemapLocations;
    private boolean lenient;
    
    /**
     * Constructor.
     */
    public DefaultSitemapResolverFactory() {
    }

    @Override
    public ISitemapResolver createSitemapResolver(
            HttpCrawlerConfig config, boolean resume) {
        return new DefaultSitemapResolver(new SitemapStore(config, resume));
    }

    public String[] getSitemapLocations() {
        return sitemapLocations;
    }
    public void setSitemapLocations(String[] sitemapLocations) {
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
}
