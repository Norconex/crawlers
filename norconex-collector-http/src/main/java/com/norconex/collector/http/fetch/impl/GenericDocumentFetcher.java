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

import static com.norconex.collector.http.fetch.impl.GenericMetadataFetcher.DEFAULT_NOT_FOUND_STATUS_CODES;
import static com.norconex.collector.http.fetch.impl.GenericMetadataFetcher.DEFAULT_VALID_STATUS_CODES;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.util.CharsetUtil;

/**
 * <p>
 * Default implementation of {@link IHttpDocumentFetcher}.
 * </p>
 * <h3>Content type and character encoding</h3>
 * <p>
 * The default behavior of the HTTP Collector to identify the content type
 * and character encoding of a document is to rely on the
 * "<a href="https://www.w3.org/Protocols/rfc1341/4_Content-Type.html">Content-Type</a>"
 * HTTP response header.  Web servers can sometimes return invalid
 * or missing content type and character encoding information. Since 2.7.0,
 * you can optionally decide not to trust web servers HTTP responses and have
 * the collector perform its own content type and encoding detection.
 * Such detection can be enabled with {@link #setDetectContentType(boolean)}
 * and {@link #setDetectCharset(boolean)}.
 * </p>
 *
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;documentFetcher
 *      class="com.norconex.collector.http.fetch.impl.GenericDocumentFetcher"
 *      detectContentType="[false|true]" detectCharset="[false|true]"&gt;
 *    &lt;validStatusCodes&gt;(defaults to 200)&lt;/validStatusCodes&gt;
 *    &lt;notFoundStatusCodes&gt;(defaults to 404)&lt;/notFoundStatusCodes&gt;
 *    &lt;headersPrefix&gt;(string to prefix headers)&lt;/headersPrefix&gt;
 *  &lt;/documentFetcher&gt;
 * </pre>
 * <p>
 * The "validStatusCodes" and "notFoundStatusCodes" elements expect a
 * coma-separated list of HTTP response code.  If a code is added in both
 * elements, the valid list takes precedence.
 * </p>
 * <p>
 * The "notFoundStatusCodes" element was added in 2.2.0.
 * </p>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following configures the document fetcher to not trust HTTP response
 * headers to identify the content type and encoding, but try to detect
 * them instead.
 * </p>
 * <pre>
 *  &lt;documentFetcher detectContentType="true" detectCharset="true"/&gt;
 * </pre>
 *
 * @author Pascal Essiembre
 */
public class GenericDocumentFetcher
        implements IHttpDocumentFetcher, IXMLConfigurable {

    private static final Logger LOG = LoggerFactory.getLogger(
			GenericDocumentFetcher.class);

    private final List<Integer> validStatusCodes =
            new ArrayList<>(DEFAULT_VALID_STATUS_CODES);
    private final List<Integer> notFoundStatusCodes =
            new ArrayList<>(DEFAULT_NOT_FOUND_STATUS_CODES);
    private String headersPrefix;
    private boolean detectContentType;
    private boolean detectCharset;
    private final transient ContentTypeDetector contentTypeDetector =
            new ContentTypeDetector();

    public GenericDocumentFetcher() {
        super();
    }
    /**
     * New document fetcher.
     * @param validStatusCodes HTTP status codes considered valid
     * @since 3.0.0
     */
    public GenericDocumentFetcher(List<Integer> validStatusCodes) {
        super();
        setValidStatusCodes(validStatusCodes);
    }
    public GenericDocumentFetcher(int[] validStatusCodes) {
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
     * @since 2.2.0
     */
    public List<Integer> getNotFoundStatusCodes() {
        return Collections.unmodifiableList(notFoundStatusCodes);
    }
    /**
     * Sets HTTP status codes to be considered as "Not found" state.
     * @param notFoundStatusCodes "Not found" codes
     * @since 2.2.0
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

    /**
     * Gets whether content type is detected instead of relying on
     * HTTP response header.
     * @return <code>true</code> to enable detection
     * @since 2.7.0
     */
	public boolean isDetectContentType() {
        return detectContentType;
    }
	/**
	 * Sets whether content type is detected instead of relying on
     * HTTP response header.
	 * @param detectContentType <code>true</code> to enable detection
     * @since 2.7.0
	 */
    public void setDetectContentType(boolean detectContentType) {
        this.detectContentType = detectContentType;
    }
    /**
     * Gets whether character encoding is detected instead of relying on
     * HTTP response header.
     * @return <code>true</code> to enable detection
     * @since 2.7.0
     */
    public boolean isDetectCharset() {
        return detectCharset;
    }
    /**
     * Sets whether character encoding is detected instead of relying on
     * HTTP response header.
     * @param detectCharset <code>true</code> to enable detection
     * @since 2.7.0
     */
    public void setDetectCharset(boolean detectCharset) {
        this.detectCharset = detectCharset;
    }

    @Override
	public HttpFetchResponse fetchDocument(
	        HttpClient httpClient, HttpDocument doc) {
	    //TODO replace signature with Writer class.
	    LOG.debug("Fetching document: {}", doc.getReference());
	    HttpRequestBase method = null;
	    try {
	        method = createUriRequest(doc);

	        // Execute the method.
            HttpResponse response = httpClient.execute(method);
            int statusCode = response.getStatusLine().getStatusCode();
            String reason = response.getStatusLine().getReasonPhrase();

            InputStream is = response.getEntity().getContent();

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
                    if (doc.getMetadata().getString(name) == null) {
                        doc.getMetadata().add(name, header.getValue());
                    }
                }

                //--- Fetch body
                doc.setContent(doc.getContent().newInputStream(is));

                //read a copy to force caching and then close the HTTP stream
                IOUtils.copy(doc.getContent(), new NullOutputStream());

                performDetection(doc);
                return new HttpFetchResponse(
                        HttpCrawlState.NEW, statusCode, reason);
            }

            // INVALID http response
            if (LOG.isTraceEnabled()) {
                LOG.trace("Rejected response content: "
                        + IOUtils.toString(is, StandardCharsets.UTF_8));
                IOUtils.closeQuietly(is);
            } else {
                // read response anyway to be safer, but ignore content
                BufferedInputStream bis = new BufferedInputStream(is);
                int result = bis.read();
                while(result != -1) {
                  result = bis.read();
                }
                IOUtils.closeQuietly(bis);
            }

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
                LOG.info("Cannot fetch document: " + doc.getReference()
                        + " (" + e.getMessage() + ")", e);
            } else {
                LOG.info("Cannot fetch document: " + doc.getReference()
                        + " (" + e.getMessage() + ")");
            }
            throw new CollectorException(e);
        } finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
	}

    private void performDetection(HttpDocument doc) throws IOException {
        if (detectContentType) {
            ContentType ct = contentTypeDetector.detect(
                    doc.getContent(), doc.getReference());
            if (ct != null) {
                doc.getMetadata().set(
                        HttpMetadata.COLLECTOR_CONTENT_TYPE, ct.toString());
            }
        }
        if (detectCharset) {
            String charset = CharsetUtil.detectCharset(doc.getContent());
            if (StringUtils.isNotBlank(charset)) {
                doc.getMetadata().set(
                        HttpMetadata.COLLECTOR_CONTENT_ENCODING, charset);
            }
        }
    }

	/**
	 * Creates the HTTP request to be executed.  Default implementation
	 * returns an {@link HttpGet} request around the document reference.
	 * This method can be overwritten to return another type of request,
	 * add HTTP headers, etc.
	 * @param doc document to fetch
	 * @return HTTP request
	 */
	protected HttpRequestBase createUriRequest(HttpDocument doc) {
	    URI uri = HttpURL.toURI(doc.getReference());
	    if (LOG.isDebugEnabled()) {
	        LOG.debug("Encoded URI: {}", uri);
	    }
	    return new HttpGet(uri);
	}

    @Override
    public void loadFromXML(XML xml) {
        setValidStatusCodes(xml.getDelimitedList(
                "validStatusCodes", Integer.class, validStatusCodes));
        setNotFoundStatusCodes(xml.getDelimitedList(
                "notFoundStatusCodes", Integer.class, notFoundStatusCodes));
        setHeadersPrefix(xml.getString("headersPrefix"));
        setDetectContentType(
                xml.getBoolean("@detectContentType", detectContentType));
        setDetectCharset(xml.getBoolean("@detectCharset", detectCharset));

    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("detectContentType", detectContentType);
        xml.setAttribute("detectCharset", detectCharset);
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