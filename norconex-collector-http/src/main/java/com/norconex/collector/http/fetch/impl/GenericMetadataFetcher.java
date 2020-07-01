/* Copyright 2010-2017 Norconex Inc.
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
import java.net.URI;

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
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.fetch.IHttpMetadataFetcher;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * <p>
 * Basic implementation of {@link IHttpMetadataFetcher}.
 * </p>
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;metadataFetcher
 *      class="com.norconex.collector.http.fetch.impl.GenericMetadataFetcher"
 *      skipOnBadStatus="[false|true]" &gt;
 *      &lt;validStatusCodes&gt;(defaults to 200)&lt;/validStatusCodes&gt;
 *      &lt;notFoundStatusCodes&gt;(defaults to 404)&lt;/notFoundStatusCodes&gt;
 *      &lt;headersPrefix&gt;(string to prefix headers)&lt;/headersPrefix&gt;
 *  &lt;/metadataFetcher&gt;
 * </pre>
 * <p>
 * The "validStatusCodes" and "notFoundStatusCodes" elements expect a
 * coma-separated list of HTTP response code.  If a code is added in both
 * elements, the valid list takes precedence.
 * </p>
 * <p>
 * The "notFoundStatusCodes" element was added in 2.6.0.
 * </p>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following configures a crawler to use this fetcher with the default
 * settings.
 * </p>
 * <pre>
 *  &lt;metadataFetcher
 *      class="com.norconex.collector.http.fetch.impl.GenericMetadataFetcher" /&gt;
 * </pre>
 *
 * @author Pascal Essiembre
 */
public class GenericMetadataFetcher
        implements IHttpMetadataFetcher, IXMLConfigurable {

    private static final Logger LOG = LogManager.getLogger(
			GenericMetadataFetcher.class);
    /*default*/ static final int[] DEFAULT_VALID_STATUS_CODES = new int[] {
        HttpStatus.SC_OK,
    };
    /*default*/ static final int[] DEFAULT_NOT_FOUND_STATUS_CODES = new int[] {
            HttpStatus.SC_NOT_FOUND,
    };

    private int[] validStatusCodes;
    private int[] notFoundStatusCodes = DEFAULT_NOT_FOUND_STATUS_CODES;
    private String headersPrefix;

    public GenericMetadataFetcher() {
        this(DEFAULT_VALID_STATUS_CODES);
    }
    public GenericMetadataFetcher(int[] validStatusCodes) {
        super();
        setValidStatusCodes(validStatusCodes);
    }
	public int[] getValidStatusCodes() {
        return ArrayUtils.clone(validStatusCodes);
    }
    public void setValidStatusCodes(int... validStatusCodes) {
        this.validStatusCodes = ArrayUtils.clone(validStatusCodes);
    }
    /**
     * Gets HTTP status codes to be considered as "Not found" state.
     * Default is 404.
     * @return "Not found" codes
     * @since 2.6.0
     */
    public int[] getNotFoundStatusCodes() {
        return ArrayUtils.clone(notFoundStatusCodes);
    }
    /**
     * Sets HTTP status codes to be considered as "Not found" state.
     * @param notFoundStatusCodes "Not found" codes
     * @since 2.6.0
     */
    public final void setNotFoundStatusCodes(int... notFoundStatusCodes) {
        this.notFoundStatusCodes = ArrayUtils.clone(notFoundStatusCodes);
    }
	public String getHeadersPrefix() {
        return headersPrefix;
    }
    public void setHeadersPrefix(String headersPrefix) {
        this.headersPrefix = headersPrefix;
    }
    @Override
    public HttpFetchResponse fetchHTTPHeaders(
            HttpClient httpClient, String url, Properties metadata) {
        LOG.debug("Fetching HTTP headers: " + url);
        HttpRequestBase method = null;
	    try {
	        method = createUriRequest(url);

	        // Execute the method.
	        HttpResponse response = httpClient.execute(method);
	        int statusCode = response.getStatusLine().getStatusCode();
	        String reason = response.getStatusLine().getReasonPhrase();

            // VALID http response
            if (ArrayUtils.contains(validStatusCodes, statusCode)) {
                //--- Fetch headers ---
                Header[] headers = response.getAllHeaders();
                for (int i = 0; i < headers.length; i++) {
                    Header header = headers[i];
                    String name = header.getName();
                    if (StringUtils.isNotBlank(headersPrefix)) {
                        name = headersPrefix + name;
                    }
                    metadata.addString(name, header.getValue());
                }
                return new HttpFetchResponse(
                        HttpCrawlState.NEW, statusCode, reason);
            }

            // INVALID http response
            if (ArrayUtils.contains(notFoundStatusCodes, statusCode)) {
                return new HttpFetchResponse(
                        HttpCrawlState.NOT_FOUND, statusCode, reason);
            }
            LOG.debug("Unsupported HTTP Response: "
                    + response.getStatusLine());
            return new HttpFetchResponse(
                    CrawlState.BAD_STATUS, statusCode, reason);
        } catch (Exception e) {
            String msg = "Cannot fetch metadata: " + url
                    + " (" + e.getMessage() + ")";
            Exception logE = LOG.isDebugEnabled() ? e : null;
            LOG.warn(msg, logE);
            throw new CollectorException(e);
        } finally {
	        if (method != null) {
	            method.releaseConnection();
	        }
        }
	}

    /**
     * Creates the HTTP request to be executed.  Default implementation
     * returns an {@link HttpHead} request around the provided URL.
     * This method can be overwritten to return another type of request.
     * @param url the URL to create the request for
     * @return HTTP request
     */
    protected HttpRequestBase createUriRequest(String url) {
        URI uri = HttpURL.toURI(url);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Encoded URI: " + uri);
        }
        return new HttpHead(uri);
    }

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = XMLConfigurationUtil.newXMLConfiguration(in);

        String validCodes = xml.getString("validStatusCodes");
        int[] intValidCodes = validStatusCodes;
        if (StringUtils.isNotBlank(validCodes)) {
            String[] strCodes = validCodes.split(",");
            intValidCodes = new int[strCodes.length];
            for (int i = 0; i < strCodes.length; i++) {
                String code = strCodes[i];
                intValidCodes[i] = Integer.parseInt(code);
            }
        }
        setValidStatusCodes(intValidCodes);

        String notFoundCodes = xml.getString("notFoundStatusCodes");
        int[] intNFCodes = notFoundStatusCodes;
        if (StringUtils.isNotBlank(notFoundCodes)) {
            String[] strCodes = notFoundCodes.split(",");
            intNFCodes = new int[strCodes.length];
            for (int i = 0; i < strCodes.length; i++) {
                String code = strCodes[i];
                intNFCodes[i] = Integer.parseInt(code);
            }
        }
        setNotFoundStatusCodes(intNFCodes);

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
            writer.writeElementString("notFoundStatusCodes",
                    StringUtils.join(notFoundStatusCodes, ','));
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
                .append(notFoundStatusCodes, castOther.notFoundStatusCodes)
                .append(headersPrefix, castOther.headersPrefix)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(validStatusCodes)
                .append(notFoundStatusCodes)
                .append(headersPrefix)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("validStatusCodes", validStatusCodes)
                .append("notFoundStatusCodes", notFoundStatusCodes)
                .append("headersPrefix", headersPrefix)
                .toString();
    }
}
