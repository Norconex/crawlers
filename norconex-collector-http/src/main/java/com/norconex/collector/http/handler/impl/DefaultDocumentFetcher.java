package com.norconex.collector.http.handler.impl;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
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
			HttpClient httpClient, HttpDocument doc) {
	    //TODO replace signature with Writer class.
	    LOG.debug("Fetching document: " + doc.getUrl());
	    HttpMethod method = null;
	    try {
	        method = new GetMethod(doc.getUrl());
	    	
	        // Execute the method.
	        int statusCode = httpClient.executeMethod(method);
	        
            InputStream is = method.getResponseBodyAsStream();
            if (ArrayUtils.contains(validStatusCodes, statusCode)) {
                //--- Fetch headers ---
                Header[] headers = method.getResponseHeaders();
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
            } else {
                // read response anyway to be safer, but ignore content
                BufferedInputStream bis = new BufferedInputStream(is);
                int result = bis.read();
                while(result != -1) {
                  result = bis.read();
                }        
                IOUtils.closeQuietly(bis);
                if (statusCode == 404) {
                    return CrawlStatus.NOT_FOUND;
                } else {
                    LOG.debug("Unsupported HTTP Response: "
                            + method.getStatusLine());
                    return CrawlStatus.BAD_STATUS;
                }
//	        	throw new HttpCollectorException("Invalid HTTP status code: "
//                        + method.getStatusLine());
	        }
			

	        //debug:
//	        byte[] responseBody = method.getResponseBody();
//	        // Deal with the response.
//	        // Use caution: ensure correct char encoding and is not binary data
//	        System.out.println(new String(responseBody));
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
    public void setValidStatusCodes(int[] validStatusCodes) {
        this.validStatusCodes = validStatusCodes;
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
    public void saveToXML(Writer out) {
        // TODO Implement me.
        System.err.println("saveToXML not implemented");
    }
}
