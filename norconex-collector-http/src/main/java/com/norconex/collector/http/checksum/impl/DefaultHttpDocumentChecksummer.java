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
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.checksum.IHttpDocumentChecksummer;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.commons.lang.config.ConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;

/**
 * Default implementation of {@link IHttpDocumentChecksummer} which 
 * returns a MD5 checksum value of the extracted document content unless
 * a given field is specified.  If a field is specified, a MD5 checksum
 * value is constructed from that field.
 * <p/>
 * Since 1.3.1 you can optionally have the checksum value stored with the
 * document under the field name {@link HttpMetadata#COLLECTOR_CHECKSUM_DOC} or one
 * you specify.
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;httpDocumentChecksummer 
 *      class="com.norconex.collector.http.checksum.impl.DefaultHttpDocumentChecksummer"&gt;
 *      field="(optional field used to create checksum)"
 *      store="[false|true]"
 *      storeField="(field to store checksum)" /&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class DefaultHttpDocumentChecksummer 
        implements IHttpDocumentChecksummer, 
        IXMLConfigurable {

	private static final long serialVersionUID = 3795335571186097378L;
	private static final Logger LOG = LogManager.getLogger(
			DefaultHttpDocumentChecksummer.class);

	private String field = null;
	private boolean store;
    private String storeField = HttpMetadata.COLLECTOR_CHECKSUM_DOC;
	
	@Override
	public String createChecksum(HttpDocument document) {
		// If field is not specified, perform checksum on whole text file.
		if (StringUtils.isNotBlank(field)) {
    		String value = document.getMetadata().getString(field);
    		if (StringUtils.isNotBlank(value)) {
    			String checksum = DigestUtils.md5Hex(value);
    			LOG.debug("Document checksum: " + checksum);
                if (isStore()) {
                    document.getMetadata().addString(getStoreField(), checksum);
                }
    			return checksum;
    		}
    		return null;
    	}
		try {
//			FileInputStream is = new FileInputStream(document.getLocalFile());
		    InputStream is = document.getContent().getInputStream();
	    	String checksum = DigestUtils.md5Hex(is);
			LOG.debug("Document checksum: " + checksum);
	    	is.close();
	    	if (isStore()) {
	    	    document.getMetadata().addString(getStoreField(), checksum);
	    	}
	    	return checksum;
		} catch (IOException e) {
			throw new CollectorException("Cannot create document checksum on : " 
			        + document.getReference(), e);
		}
    }

	/**
	 * Gets the specific field to construct a MD5 checksum on.  Default
	 * is <code>null</code> (checksum is performed on entire content).
	 * @return field to perform checksum on
	 */
	public String getField() {
		return field;
	}
    /**
     * Sets the specific field to construct a MD5 checksum on.  Specifying
     * <code>null</code> means all content will be used.
     * @param field field to perform checksum on
     */
	public void setField(String field) {
		this.field = field;
	}

	/**
	 * Whether to store the document checksum value as a new field in the 
	 * document metadata.
	 * @return <code>true</code> to store the checksum
	 */
	public boolean isStore() {
        return store;
    }
    /**
     * Sets whether to store the document checksum value as a new field in the 
     * document metadata. 
     * @param store <code>true</code> to store the checksum
     */
    public void setStore(boolean store) {
        this.store = store;
    }

    /**
     * Gets the metadata field to use to store the checksum value.
     * Defaults to {@link HttpMetadata#COLLECTOR_CHECKSUM_DOC}.  Only applicable
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
            LOG.warn("<field> tag is now deprecated.  Use \"field\" attribute "
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
            writer.writeAttribute("field", getField());
            writer.writeAttribute("store", Boolean.toString(isStore()));
            writer.writeAttribute("storeField", getStoreField());
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }        
    }

}
