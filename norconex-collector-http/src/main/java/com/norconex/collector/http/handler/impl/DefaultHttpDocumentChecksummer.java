package com.norconex.collector.http.handler.impl;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.HttpCollectorException;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.handler.IHttpDocumentChecksummer;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;

/**
 * Default implementation of {@link IHttpDocumentChecksummer} which 
 * returns a MD5 checksum value of the extracted document content unless
 * a given field is specified.  If a field is specified, a MD5 checksum
 * value is constructed from that field.
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;httpDocumentChecksummer class="com.norconex.collector.http.handler.DefaultHttpDocumentChecksummer"&gt;
 *      &lt;field&gt;(optional field name)&lt;/field&gt;
 *  &lt;/httpDocumentChecksummer &gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class DefaultHttpDocumentChecksummer 
        implements IHttpDocumentChecksummer, 
        IXMLConfigurable {

	private static final long serialVersionUID = 3795335571186097378L;
	private static final Logger LOG = LogManager.getLogger(
			DefaultHttpDocumentChecksummer.class);

	private String field = null;
	
	@Override
	public String createChecksum(HttpDocument document) {
		// If field is not specified, perform checksum on whole text file.
		if (StringUtils.isNotBlank(field)) {
    		String value = document.getMetadata().getString(field);
    		if (StringUtils.isNotBlank(value)) {
    			String checksum = DigestUtils.md5Hex(value);;
    			LOG.debug("Document checksum: " + checksum);
    			return checksum;
    		}
    		return null;
    	}
		try {
			FileInputStream is = new FileInputStream(document.getLocalFile());
	    	String checksum = DigestUtils.md5Hex(is);
			LOG.debug("Document checksum: " + checksum);
	    	is.close();
	    	return checksum;
		} catch (IOException e) {
			throw new HttpCollectorException(
					"Cannot create checksum on : " + document.getLocalFile());
		}
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
        setField(xml.getString("field", null));
    }
    @Override
    public void saveToXML(Writer out) {
        // TODO Implement me.
        System.err.println("saveToXML not implemented");
    }

}
