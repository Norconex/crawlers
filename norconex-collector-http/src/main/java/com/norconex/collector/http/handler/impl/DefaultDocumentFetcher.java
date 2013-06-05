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
package com.norconex.collector.http.handler.impl;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.crawler.CrawlStatus;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.handler.IHttpDocumentFetcher;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;

/**
 * Default implementation of {@link IHttpDocumentFetcher}.  
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;httpDocumentFetcher  
 *      class="com.norconex.collector.http.handler.impl.DefaultDocumentFetcher"&gt;
 *      &lt;validStatusCodes&gt;200&lt;/validStatusCodes&gt;
 *      &lt;headersPrefix&gt;(string to prefix headers)&lt;/headersPrefix&gt;
 *  &lt;/httpDocumentFetcher&gt;
 * </pre>
 * <p>
 * The "validStatusCodes" attribute expects a coma-separated list of HTTP
 * response code.
 * </p>
 * @author Pascal Essiembre
 */
public class DefaultDocumentFetcher 
        implements IHttpDocumentFetcher, 
                   IXMLConfigurable {

	private static final long serialVersionUID = -6523482835344340418L;
    private static final Logger LOG = LogManager.getLogger(
			DefaultDocumentFetcher.class);
    private int[] validStatusCodes;
    private String headersPrefix;
    
    public DefaultDocumentFetcher() {
        this(SimpleHttpHeadersFetcher.DEFAULT_VALID_STATUS_CODES);
    }
    public DefaultDocumentFetcher(int[] validStatusCodes) {
        super();
        setValidStatusCodes(validStatusCodes);
    }
    
    
	@Override
	public CrawlStatus fetchDocument(
	        DefaultHttpClient httpClient, HttpDocument doc) {
	    //TODO replace signature with Writer class.
	    LOG.debug("Fetching document: " + doc.getUrl());
	    HttpGet method = null;
	    try {
	        method = new HttpGet(doc.getUrl());
	    	
	        // Execute the method.
            HttpResponse response = httpClient.execute(method);
            int statusCode = response.getStatusLine().getStatusCode();

	        
            InputStream is = response.getEntity().getContent();
            if (ArrayUtils.contains(validStatusCodes, statusCode)) {
                //--- Fetch headers ---
                Header[] headers = response.getAllHeaders();
                for (int i = 0; i < headers.length; i++) {
                    Header header = headers[i];
                    String name = header.getName();
                    if (StringUtils.isNotBlank(headersPrefix)) {
                        name = headersPrefix + name;
                    }
                    if (doc.getMetadata().getString(name) == null) {
                        doc.getMetadata().addString(name, header.getValue());
                    }
                }
                
                //--- Fetch body
                FileOutputStream os = FileUtils.openOutputStream(
                        doc.getLocalFile());
                IOUtils.copy(is, os);
                IOUtils.closeQuietly(is);
                IOUtils.closeQuietly(os);
                return CrawlStatus.OK;
            }
            // read response anyway to be safer, but ignore content
            BufferedInputStream bis = new BufferedInputStream(is);
            int result = bis.read();
            while(result != -1) {
              result = bis.read();
            }        
            IOUtils.closeQuietly(bis);
            if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return CrawlStatus.NOT_FOUND;
            }
            LOG.debug("Unsupported HTTP Response: "
                    + response.getStatusLine());
            return CrawlStatus.BAD_STATUS;
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.error("Cannot fetch document: " + doc.getUrl()
                        + " (" + e.getMessage() + ")", e);
            } else {
                LOG.error("Cannot fetch document: " + doc.getUrl()
                        + " (" + e.getMessage() + ")");
            }
            throw new HttpCollectorException(e);
        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }  
	}
	
    public int[] getValidStatusCodes() {
        return validStatusCodes;
    }
    public final void setValidStatusCodes(int[] validStatusCodes) {
        this.validStatusCodes = ArrayUtils.clone(validStatusCodes);
    }
    public String getHeadersPrefix() {
        return headersPrefix;
    }
    public void setHeadersPrefix(String headersPrefix) {
        this.headersPrefix = headersPrefix;
    }
    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        String validCodes = xml.getString("validStatusCodes");
        int[] intCodes = SimpleHttpHeadersFetcher.DEFAULT_VALID_STATUS_CODES;
        if (StringUtils.isNotBlank(validCodes)) {
            String[] strCodes = validCodes.split(",");
            intCodes = new int[strCodes.length];
            for (int i = 0; i < strCodes.length; i++) {
                String code = strCodes[i];
                intCodes[i] = Integer.parseInt(code);
            }
        }
        setHeadersPrefix(xml.getString("headersPrefix"));
        setValidStatusCodes(intCodes);
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("httpDocumentFetcher");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeStartElement("validStatusCodes");
            if (validStatusCodes != null) {
                writer.writeCharacters(StringUtils.join(validStatusCodes));
            }
            writer.writeEndElement();
            writer.writeStartElement("headersPrefix");
            if (headersPrefix != null) {
                writer.writeCharacters(headersPrefix);
            }
            writer.writeEndElement();
            writer.writeEndElement();
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }        
    }
}

