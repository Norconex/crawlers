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
package com.norconex.collector.http.checksum.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.checksum.IHttpHeadersChecksummer;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;

/**
 * Default implementation of {@link IHttpHeadersChecksummer} which 
 * simply returns the exact value of the "Last-Modified" HTTP header if no 
 * alternate header is specified.
 * <p/>
 * Since 1.3.1 you can optionally have the checksum value stored with the
 * document under the field name {@link HttpMetadata#COLLECTOR_CHECKSUM_HEADER} or one
 * you specify.
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;httpHeadersChecksummer 
 *      class="com.norconex.collector.http.checksum.impl.DefaultHttpHeadersChecksummer"&gt;
 *      field="(optional header field used to create checksum)"
 *      store="[false|true]"
 *      storeField="(field to store checksum)" /&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class DefaultHttpHeadersChecksummer 
        implements IHttpHeadersChecksummer, 
        IXMLConfigurable {

	private static final long serialVersionUID = -6759418012119786557L;
	private static final Logger LOG = LogManager.getLogger(
			DefaultHttpHeadersChecksummer.class);

	/**
	 * Default HTTP header name used to perform checksum.
	 */
	public static final String DEFAULT_FIELD = "Last-Modified";
	
	private String field = DEFAULT_FIELD;
    private boolean store;
    private String storeField = HttpMetadata.COLLECTOR_CHECKSUM_HEADER;
	
    @Override
    public String createChecksum(Properties metadata) {
    	if (StringUtils.isNotBlank(field)) {
    		String checksum = metadata.getString(field);
			LOG.debug("Headers checksum: " + checksum);
    		return checksum;
    	}
    	return null;
    }

    /**
     * Gets the HTTP header name used to perform checksum.
     * @return HTTP header name
     */
    public String getField() {
		return field;
	}
    /**
     * Sets the HTTP header name used to perform checksum.
     * @param field HTTP header name
     */
	public void setField(String field) {
		this.field = field;
	}
	
    /**
     * Whether to store the header checksum value as a new field in the 
     * document metadata.
     * @return <code>true</code> to store the checksum
     */
    public boolean isStore() {
        return store;
    }
    /**
     * Sets whether to store the header checksum value as a new field in the 
     * document metadata. 
     * @param store <code>true</code> to store the checksum
     */
    public void setStore(boolean store) {
        this.store = store;
    }

    /**
     * Gets the metadata field to use to store the checksum value.
     * Defaults to {@link HttpMetadata#COLLECTOR_CHECKSUM_HEADER}.  Only applicable
     * if {@link #isStore()} returns {@code true}
     * @return metadata field name
     */
    public String getStoreField() {
        return storeField;
    }
    /**
     * Sets the metadata field name to use to store the checksum value.
     * @param storeField the metadata field name
     */
    public void setStoreField(String storeField) {
        this.storeField = storeField;
    }	

	@Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationUtil.newXMLConfiguration(in);
        String tagField = xml.getString("field", null);
        String attrField = xml.getString("[@field]", field);

        if (StringUtils.isNotBlank(tagField)) {
            LOG.warn("<field> tag is not deprecated.  Use \"field\" attribute "
                    + "instead");
        }
        if (StringUtils.isNotBlank(attrField)) {
            setField(attrField);
        } else if (StringUtils.isNotBlank(tagField)) {
            setField(tagField);
        }
        setStore(xml.getBoolean("[@store]", store));
        setStoreField(xml.getString("[@storeField]", storeField));
	}
    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("httpDocumentChecksummer");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeStartElement("field");
            writer.writeCharacters(field);
            writer.writeEndElement();
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        } 
    }
}
