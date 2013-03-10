package com.norconex.collector.http.handler.impl;

import java.io.Reader;
import java.io.Writer;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.meta.Metadata;
import com.norconex.collector.http.handler.IHttpHeadersChecksummer;
import com.norconex.collector.http.util.QuietConfigurationLoader;

/**
 * Default implementation of {@link IHttpHeadersChecksummer} which 
 * simply returns the exact value of the "Last-Modified" HTTP header if no 
 * alternate header is specified.
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;httpHeadersChecksummer class="com.norconex.collector.http.handler.DefaultHttpHeadersChecksummer"&gt;
 *      &lt;header&gt;(optional alternate header field name)&lt;/header&gt;
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
    public String createChecksum(Metadata metadata) {
    	if (StringUtils.isNotBlank(field)) {
    		String checksum = metadata.getPropertyValue(field);
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
        XMLConfiguration xml = QuietConfigurationLoader.load(in);
        setField(xml.getString("field", DEFAULT_FIELD));
    }
    @Override
    public void saveToXML(Writer out) {
        // TODO Implement me.
        System.err.println("saveToXML not implemented");
    }
}
