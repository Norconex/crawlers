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
package com.norconex.collector.http.fetch.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.fetch.IHttpHeadersFetcher;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;

/**
 * Basic implementation of {@link IHttpHeadersFetcher}.  
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;httpHeadersFetcher 
 *      class="com.norconex.collector.http.fetch.impl.SimpleHttpHeadersFetcher" &gt;
 *      &lt;validStatusCodes&gt;200&lt;/validStatusCodes&gt;
 *      &lt;headersPrefix&gt;(string to prefix headers)&lt;/headersPrefix&gt;
 *  &lt;/httpHeadersFetcher&gt;
 * </pre>
 * <p>
 * The "validStatusCodes" attribute expects a coma-separated list of HTTP
 * response code.
 * </p>
 * @author Pascal Essiembre
 */
public class SimpleHttpHeadersFetcher 
        implements IHttpHeadersFetcher, IXMLConfigurable {

	private static final long serialVersionUID = 6526443843689019304L;
    private static final Logger LOG = LogManager.getLogger(
			SimpleHttpHeadersFetcher.class);
    public static final int[] DEFAULT_VALID_STATUS_CODES = new int[] {
        HttpStatus.SC_OK,
    };
	
    private int[] validStatusCodes;
    private String headersPrefix;

    public SimpleHttpHeadersFetcher() {
        this(DEFAULT_VALID_STATUS_CODES);
    }
    public SimpleHttpHeadersFetcher(int[] validStatusCodes) {
        super();
        this.validStatusCodes = ArrayUtils.clone(validStatusCodes);
    }
	public int[] getValidStatusCodes() {
        return validStatusCodes;
    }
    public void setValidStatusCodes(int[] validStatusCodes) {
        this.validStatusCodes = ArrayUtils.clone(validStatusCodes);
    }
	public String getHeadersPrefix() {
        return headersPrefix;
    }
    public void setHeadersPrefix(String headersPrefix) {
        this.headersPrefix = headersPrefix;
    }
    @Override
	public Properties fetchHTTPHeaders(
	        HttpClient httpClient, String url) {
	    Properties metadata = new Properties();
	    HttpHead method = null;
	    try {
	        method = new HttpHead(url);
	        // Execute the method.
	        HttpResponse response = httpClient.execute(method);
	        int statusCode = response.getStatusLine().getStatusCode();
	        if (!ArrayUtils.contains(validStatusCodes, statusCode)) {
	            if (LOG.isDebugEnabled()) {
	                LOG.debug("Invalid HTTP status code ("
	                        + response.getStatusLine() + ") for URL: " + url);
	            }
	            return null;
	        }
	        
	        Header[] headers = response.getAllHeaders();
	        for (int i = 0; i < headers.length; i++) {
	            Header header = headers[i];
	            String name = header.getName();
	            if (StringUtils.isNotBlank(headersPrefix)) {
	            	name = headersPrefix + name;
	            }
	            
	            metadata.addString(name, header.getValue());
	        }
	        return metadata;
        } catch (Exception e) {
        	LOG.error("Cannot fetch document: " + url
        	        + " (" + e.getMessage() + ")", e);
        	throw new HttpCollectorException(e);
        } finally {
	        if (method != null) {
	            method.releaseConnection();
	        }
        }  
	}
	
    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        String validCodes = xml.getString("validStatusCodes");
        int[] intCodes = DEFAULT_VALID_STATUS_CODES;
        if (StringUtils.isNotBlank(validCodes)) {
            String[] strCodes = validCodes.split(",");
            intCodes = new int[strCodes.length];
            for (int i = 0; i < strCodes.length; i++) {
                String code = strCodes[i];
                intCodes[i] = Integer.parseInt(code);
            }
        }
        setValidStatusCodes(intCodes);
        setHeadersPrefix(xml.getString("headersPrefix"));
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("httpHeadersFetcher");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeStartElement("validStatusCodes");
            writer.writeCharacters(StringUtils.join(validStatusCodes, ","));
            writer.writeEndElement();
            writer.writeStartElement("headersPrefix");
            writer.writeCharacters(headersPrefix);
            writer.writeEndElement();
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
}
