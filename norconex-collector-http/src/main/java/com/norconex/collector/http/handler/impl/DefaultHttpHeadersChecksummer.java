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

import com.norconex.collector.http.handler.IHttpHeadersChecksummer;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;

/**
 * Default implementation of {@link IHttpHeadersChecksummer} which 
 * simply returns the exact value of the "Last-Modified" HTTP header if no 
 * alternate header is specified.
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;httpHeadersChecksummer class="com.norconex.collector.http.handler.DefaultHttpHeadersChecksummer"&gt;
 *      &lt;field&gt;(optional alternate header field name)&lt;/field&gt;
 *  &lt;/httpHeadersChecksummer&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class DefaultHttpHeadersChecksummer 
        implements IHttpHeadersChecksummer, 
        IXMLConfigurable {

	private static final long serialVersionUID = -6759418012119786557L;
	private static final Logger LOG = LogManager.getLogger(
			DefaultHttpHeadersChecksummer.class);

	public static final String DEFAULT_FIELD = "Last-Modified";
	
	private String field = DEFAULT_FIELD;
	
    @Override
    public String createChecksum(Properties metadata) {
    	if (StringUtils.isNotBlank(field)) {
    		String checksum = metadata.getString(field);
			LOG.debug("Headers checksum: " + checksum);
    		return checksum;
    	}
    	return null;
    }
    
    public String getField() {
		return field;
	}
	public void setField(String field) {
		this.field = field;
	}



	@Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setField(xml.getString("field", DEFAULT_FIELD));
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
