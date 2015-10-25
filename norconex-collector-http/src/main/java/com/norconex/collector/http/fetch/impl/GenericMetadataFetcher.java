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
package com.norconex.collector.http.fetch.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.fetch.IHttpMetadataFetcher;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * Basic implementation of {@link IHttpMetadataFetcher}.  
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;metadataFetcher 
 *      class="com.norconex.collector.http.fetch.impl.GenericMetadataFetcher" &gt;
 *      &lt;validStatusCodes&gt;200&lt;/validStatusCodes&gt;
 *      &lt;headersPrefix&gt;(string to prefix headers)&lt;/headersPrefix&gt;
 *  &lt;/metadataFetcher&gt;
 * </pre>
 * <p>
 * The "validStatusCodes" attribute expects a coma-separated list of HTTP
 * response code.
 * </p>
 * @author Pascal Essiembre
 */
public class GenericMetadataFetcher 
        implements IHttpMetadataFetcher, IXMLConfigurable {

    private static final Logger LOG = LogManager.getLogger(
			GenericMetadataFetcher.class);
    /*default*/ static final int[] DEFAULT_VALID_STATUS_CODES = new int[] {
        HttpStatus.SC_OK,
    };
	
    private int[] validStatusCodes;
    private String headersPrefix;

    public GenericMetadataFetcher() {
        this(DEFAULT_VALID_STATUS_CODES);
    }
    public GenericMetadataFetcher(int[] validStatusCodes) {
        super();
        this.validStatusCodes = ArrayUtils.clone(validStatusCodes);
    }
	public int[] getValidStatusCodes() {
        return ArrayUtils.clone(validStatusCodes);
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
        	throw new CollectorException(e);
        } finally {
	        if (method != null) {
	            method.releaseConnection();
	        }
        }  
	}
	
    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
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
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);         
            writer.writeStartElement("metadataFetcher");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeElementString("validStatusCodes", 
                    StringUtils.join(validStatusCodes, ','));
            writer.writeElementString("headersPrefix", headersPrefix);
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
    
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof GenericMetadataFetcher)) {
            return false;
        }
        GenericMetadataFetcher castOther = (GenericMetadataFetcher) other;
        return new EqualsBuilder()
                .append(validStatusCodes, castOther.validStatusCodes)
                .append(headersPrefix, castOther.headersPrefix)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(validStatusCodes)
                .append(headersPrefix)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("validStatusCodes", validStatusCodes)
                .append("headersPrefix", headersPrefix)
                .toString();
    }
}
