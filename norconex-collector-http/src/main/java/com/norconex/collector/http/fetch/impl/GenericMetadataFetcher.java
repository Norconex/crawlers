/* Copyright 2010-2018 Norconex Inc.
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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.fetch.IHttpMetadataFetcher;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Basic implementation of {@link IHttpMetadataFetcher}.
 * </p>
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;metadataFetcher
 *      class="com.norconex.collector.http.fetch.impl.GenericMetadataFetcher" &gt;
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

    private static final Logger LOG = LoggerFactory.getLogger(
			GenericMetadataFetcher.class);

    static final List<Integer> DEFAULT_VALID_STATUS_CODES =
            Collections.unmodifiableList(Arrays.asList(HttpStatus.SC_OK));
    static final List<Integer> DEFAULT_NOT_FOUND_STATUS_CODES =
            Collections.unmodifiableList(Arrays.asList(HttpStatus.SC_NOT_FOUND));

    private final List<Integer> validStatusCodes =
            new ArrayList<>(DEFAULT_VALID_STATUS_CODES);
    private final List<Integer> notFoundStatusCodes =
            new ArrayList<>(DEFAULT_NOT_FOUND_STATUS_CODES);
    private String headersPrefix;

    public GenericMetadataFetcher() {
        super();
    }
    /**
     * New metadata fetcher.
     * @param validStatusCodes HTTP status codes considered valid
     * @since 3.0.0
     */
    public GenericMetadataFetcher(List<Integer> validStatusCodes) {
        super();
        setValidStatusCodes(validStatusCodes);
    }
    public GenericMetadataFetcher(int[] validStatusCodes) {
        super();
        setValidStatusCodes(validStatusCodes);
    }

    public List<Integer> getValidStatusCodes() {
	    return Collections.unmodifiableList(validStatusCodes);
    }
	/**
	 * Gets valid HTTP response status codes.
	 * @param validStatusCodes valid status codes
	 * @since 3.0.0
	 */
    public void setValidStatusCodes(List<Integer> validStatusCodes) {
        CollectionUtil.setAll(this.validStatusCodes, validStatusCodes);
    }
    /**
     * Gets valid HTTP response status codes.
     * @param validStatusCodes valid status codes
     */
    public void setValidStatusCodes(int... validStatusCodes) {
        CollectionUtil.setAll(this.validStatusCodes,
                ArrayUtils.toObject(validStatusCodes));
    }

    /**
     * Gets HTTP status codes to be considered as "Not found" state.
     * Default is 404.
     * @return "Not found" codes
     * @since 2.6.0
     */
    public List<Integer> getNotFoundStatusCodes() {
        return Collections.unmodifiableList(notFoundStatusCodes);
    }
    /**
     * Sets HTTP status codes to be considered as "Not found" state.
     * @param notFoundStatusCodes "Not found" codes
     * @since 2.6.0
     */
    public final void setNotFoundStatusCodes(int... notFoundStatusCodes) {
        CollectionUtil.setAll(this.notFoundStatusCodes,
                ArrayUtils.toObject(notFoundStatusCodes));
    }
    /**
     * Sets HTTP status codes to be considered as "Not found" state.
     * @param notFoundStatusCodes "Not found" codes
     * @since 3.0.0
     */
    public final void setNotFoundStatusCodes(
            List<Integer> notFoundStatusCodes) {
        CollectionUtil.setAll(this.notFoundStatusCodes, notFoundStatusCodes);
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
        LOG.debug("Fetching HTTP headers: {}", url);
        HttpRequestBase method = null;
	    try {
	        method = createUriRequest(url);

	        // Execute the method.
	        HttpResponse response = httpClient.execute(method);
	        int statusCode = response.getStatusLine().getStatusCode();
	        String reason = response.getStatusLine().getReasonPhrase();

            // VALID http response
            if (validStatusCodes.contains(statusCode)) {
                //--- Fetch headers ---
                Header[] headers = response.getAllHeaders();
                for (int i = 0; i < headers.length; i++) {
                    Header header = headers[i];
                    String name = header.getName();
                    if (StringUtils.isNotBlank(headersPrefix)) {
                        name = headersPrefix + name;
                    }
                    metadata.add(name, header.getValue());
                }
                return new HttpFetchResponse(
                        HttpCrawlState.NEW, statusCode, reason);
            }

            // INVALID http response
            if (notFoundStatusCodes.contains(statusCode)) {
                return new HttpFetchResponse(
                        HttpCrawlState.NOT_FOUND, statusCode, reason);
            }
            LOG.debug("Unsupported HTTP Response: "
                    + response.getStatusLine());
            return new HttpFetchResponse(
                    CrawlState.BAD_STATUS, statusCode, reason);
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.error("Cannot fetch metadata: " + url
                        + " (" + e.getMessage() + ")", e);
            } else {
                LOG.error("Cannot fetch metadata: " + url
                        + " (" + e.getMessage() + ")");
            }
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
            LOG.debug("Encoded URI: {}", uri);
        }
        return new HttpHead(uri);
    }

    @Override
    public void loadFromXML(XML xml) {
        setValidStatusCodes(xml.getDelimitedList(
                "validStatusCodes", Integer.class, validStatusCodes));
        setNotFoundStatusCodes(xml.getDelimitedList(
                "notFoundStatusCodes", Integer.class, notFoundStatusCodes));
        setHeadersPrefix(xml.getString("headersPrefix"));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.addDelimitedElementList("validStatusCodes", validStatusCodes);
        xml.addDelimitedElementList("notFoundStatusCodes", notFoundStatusCodes);
        xml.addElement("headersPrefix", headersPrefix);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
