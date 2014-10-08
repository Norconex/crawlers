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

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.checksum.AbstractMetadataChecksummer;
import com.norconex.collector.core.checksum.IMetadataChecksummer;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * Default implementation of {@link IMetadataChecksummer} which by default
 * returns the exact value of the "Last-Modified" HTTP header field.
 * <p/>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;metadataChecksummer 
 *      class="com.norconex.collector.http.checksum.impl.HttpMetadataChecksummer"&gt;
 *      sourceField="(optional header field used to create checksum)"
 *      keep="[false|true]"
 *      targetField="(field to store checksum)" /&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class HttpMetadataChecksummer extends AbstractMetadataChecksummer {

	private static final Logger LOG = LogManager.getLogger(
			HttpMetadataChecksummer.class);

	/**
	 * Default HTTP header name used to perform checksum.
	 */
	public static final String DEFAULT_FIELD = "Last-Modified";
	
	private String sourceField = DEFAULT_FIELD;
	
    @Override
    protected String doCreateMetaChecksum(Properties metadata) {
    	if (StringUtils.isNotBlank(sourceField)) {
    		String checksum = metadata.getString(sourceField);
			LOG.debug("Headers checksum: " + checksum);
    		return checksum;
    	}
    	return null;
    }

    /**
     * Gets the HTTP header name used to perform checksum.
     * @return HTTP header name
     */
    public String getSourceField() {
		return sourceField;
	}
    /**
     * Sets the HTTP header name used to perform checksum.
     * @param field HTTP header name
     */
	public void setSourceField(String field) {
		this.sourceField = field;
	}
	
    @Override
    protected void loadChecksummerFromXML(XMLConfiguration xml) {
        String attrField = xml.getString("[@sourceField]", sourceField);
        setSourceField(attrField);
    }

    @Override
    protected void saveChecksummerToXML(EnhancedXMLStreamWriter writer)
            throws XMLStreamException {
        writer.writeAttributeString("sourceField", sourceField);
    }
}
