/* Copyright 2020 Norconex Inc.
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
package com.norconex.collector.http.fetch.util;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.doc.CrawlDocInfo;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.impl.HttpAuthConfig;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.importer.doc.DocInfo;

/**
 * Utility methods for fetcher implementations using Apache HttpClient.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public final class ApacheHttpUtil {

    private static final Logger LOG =
            LoggerFactory.getLogger(ApacheHttpUtil.class);

    private ApacheHttpUtil() {
        super();
    }

    /**
     * <p>
     * Applies the HTTP response content to a document if such content exists.
     * The stream is fully downloaded and associated with a document.
     * </p>
     * @param response the HTTP response
     * @param doc document to apply headers on
     * @return <code>true</code> if there was content to apply
     * @throws IOException could not read existing content
     */
    public static boolean applyResponseContent(
            HttpResponse response, CrawlDoc doc) throws IOException {

        HttpEntity entity = response.getEntity();
        if (entity == null) {
            // just in case...
            EntityUtils.consumeQuietly(entity);
            return false;
        }
        try (CachedInputStream content =
                doc.getStreamFactory().newInputStream(entity.getContent())) {
            content.enforceFullCaching();
            doc.setInputStream(content);
        } finally {
            // just in case...
            EntityUtils.consumeQuietly(entity);
        }
        return true;
    }

    /**
     * <p>
     * Applies the HTTP response headers to a document. This method will
     * do its best to derive relevant information from the HTTP headers
     * that can be set on the document {@link DocInfo}:
     * </p>
     * <ul>
     *   <li>Content type</li>
     *   <li>Content encoding</li>
     *   <!--<li>Document checksum</li>-->
     * </ul>
     * <p>
     * In addition, all HTTP headers will be added to the document metadata,
     * with an optional prefix.
     * </p>
     * @param response the HTTP response
     * @param prefix optional metadata prefix for all HTTP response headers
     * @param doc document to apply headers on
     */
    public static void applyResponseHeaders(
            HttpResponse response, String prefix, CrawlDoc doc) {
        HttpDocInfo docInfo = (HttpDocInfo) doc.getDocInfo();
        HeaderIterator it = response.headerIterator();
        while (it.hasNext()) {
            Header header = (Header) it.next();
            String name = header.getName();
            String value = header.getValue();

            if (StringUtils.isBlank(value)) {
                continue;
            }

            // Content-Type + Content Encoding (Charset)
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                applyContentTypeAndCharset(value, docInfo);
            }

//Have people grab it from metadata if they want to use it for checksum
//            // MD5 Checksum
//            if (HttpHeaders.CONTENT_MD5.equalsIgnoreCase(name)) {
//                docInfo.setMetaChecksum(value);
//            }

            if (StringUtils.isNotBlank(prefix)) {
                name = prefix + name;
            }
            PropertySetter.OPTIONAL.apply(doc.getMetadata(), name, value);
        }
    }

    /**
     * Applies the <code>Content-Type</code> HTTP response header
     * on the supplied document info.  It does so by extracting both
     * the content type and charset from the value, and sets them by invoking
     * {@link DocInfo#setContentType(ContentType)} and
     * {@link DocInfo#setContentEncoding(String)}.
     * This method is automatically invoked by
     * {@link #applyResponseHeaders(HttpResponse, String, CrawlDoc)}
     * when encountering a content type header.
     * @param value value to parse and set.
     * @param docInfo document info
     */
    public static void applyContentTypeAndCharset(
            String value, CrawlDocInfo docInfo) {
        if (StringUtils.isBlank(value) || docInfo == null) {
            return;
        }
        // delegate parsing of content-type honoring various forms
        // https://tools.ietf.org/html/rfc7231#section-3.1.1
        org.apache.http.entity.ContentType apacheCT =
                org.apache.http.entity.ContentType.parse(value);

        // only overwrite object properties if not null
        ContentType ct = ContentType.valueOf(apacheCT.getMimeType());
        if (ct != null) {
            docInfo.setContentType(ct);
        }
        Charset charset = apacheCT.getCharset();
        if (charset != null) {
            docInfo.setContentEncoding(charset.toString());
        }
    }


    /**
     * Sets the <code>If-Modified-Since</code> HTTP requeset header based
     * on document last crawled date.
     * @param request HTTP request
     * @param doc document
     */
    //TODO abstract authentication (e.g., factory)
    public static void setRequestIfModifiedSince(
            HttpRequest request, CrawlDoc doc) {
        if (doc.hasCache()) {
            ZonedDateTime zdt = doc.getCachedDocInfo().getCrawlDate();
            if (zdt != null) {
                request.addHeader(HttpHeaders.IF_MODIFIED_SINCE,
                        zdt.format(DateTimeFormatter.RFC_1123_DATE_TIME));
            }
        }
    }

    /**
     * Creates an HTTP request.
     * @param url the request target URL
     * @param method HTTP method (defaults to GET if <code>null</code>)
     * @return Apache HTTP request
     */
    public static HttpRequestBase createUriRequest(String url, String method) {
        String m = defaultIfNull(method, "GET");
        return createUriRequest(url, HttpMethod.valueOf(m.toUpperCase()));
    }
    /**
     * Creates an HTTP request.
     * @param url the request target URL
     * @param method HTTP method (defaults to GET if <code>null</code>)
     * @return Apache HTTP request
     */
    public static HttpRequestBase createUriRequest(
            String url, HttpMethod method) {
        URI uri = HttpURL.toURI(url);
        LOG.debug("Encoded URI: {}", uri);
        switch (method) {
        case HEAD:
            return new HttpHead(uri);
        case POST:
            return new HttpPost(uri);
        default:
            return new HttpGet(uri);
        }
    }

    public static void authenticateUsingForm(
            HttpClient httpClient, HttpAuthConfig authConfig)
                    throws IOException {
        if (authConfig == null) {
            return;
        }

        Objects.requireNonNull(authConfig.getUrl(),
                "Authentication URL must not be null.");

        if (StringUtils.isBlank(authConfig.getFormSelector())) {
            authFormAction(httpClient, authConfig);
        } else {
            authLoginPage(httpClient, authConfig);
        }
    }

    private static void authFormAction(
            HttpClient httpClient, HttpAuthConfig cfg)
                    throws IOException {
        HttpPost post = new HttpPost(cfg.getUrl());

        List<NameValuePair> formparams = new ArrayList<>();
        formparams.add(new BasicNameValuePair(
                cfg.getFormUsernameField(),
                cfg.getCredentials().getUsername()));
        formparams.add(new BasicNameValuePair(
                cfg.getFormPasswordField(),
                EncryptionUtil.decryptPassword(cfg.getCredentials())));

        for (String name : cfg.getFormParamNames()) {
            formparams.add(new BasicNameValuePair(
                    name, cfg.getFormParam(name)));
        }

        LOG.info("Performing FORM authentication at \"{}\" (username={}; p"
                + "assword=*****)", cfg.getUrl(),
                cfg.getCredentials().getUsername());
        try {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(
                    formparams, cfg.getFormCharset());
            post.setEntity(entity);
            HttpResponse response =
                    httpClient.execute(post, (HttpContext) null);
            LOG.info("Authentication status: {}.", response.getStatusLine());

            if (LOG.isDebugEnabled()) {
                LOG.debug("Authentication response:\n{}", IOUtils.toString(
                        response.getEntity().getContent(),
                        StandardCharsets.UTF_8));
            }
        } finally {
            post.releaseConnection();
        }
    }

    private static void authLoginPage(
            HttpClient httpClient, HttpAuthConfig cfg)
                    throws IOException {

        LOG.info("Parsing, filing, and submitting login FORM at \"{}\" "
                + "(username={}; p"
                + "assword=*****)", cfg.getUrl(),
                cfg.getCredentials().getUsername());
        HttpGet get = new HttpGet(cfg.getUrl());
        try {
            HttpResponse response =
                    httpClient.execute(get, (HttpContext) null);
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                LOG.error("Authentication URL returned no content. "
                        + "Status: {}.", response.getStatusLine());
                return;
            }
            Document doc = Jsoup.parse(
                    entity.getContent(),
                    entity.getContentEncoding().getValue(),
                    cfg.getUrl());
            HttpUriRequest authReq = formToRequest(doc, cfg);
            if (authReq != null) {
                //TODO execute auth request
            }
            //TODO share some of auth code with authFormAction(...)
        } finally {
            get.releaseConnection();
        }




    }

    private static HttpUriRequest formToRequest(
            Document doc, HttpAuthConfig cfg) {
        if (true) {
            throw new UnsupportedOperationException(
                    "This type of authentication is not yet supported. "
                  + "Remove 'formSelector' for classic form-auth.");
        }
        Element form = doc.selectFirst(cfg.getFormSelector());
        if (form == null) {
            LOG.error("Page has no login form: {}", cfg.getUrl());
            return null;
        }

        List<NameValuePair> params = new ArrayList<>();
        // Loop through each input and fill/overwrite matching
        // ones, else take as is.
        for (Element el: form.select("[name]")) {
            String name = el.attr("name");
            String value = el.val();
            if (StringUtils.isBlank(value)) {
                value = el.wholeText();
            }

            // check for matches and overite

        }


        // todo if config form params were not encountered, add them.

        // If no "action", we assume self.
        String actionURL = defaultIfNull(form.attr("abs:action"), cfg.getUrl());

        HttpUriRequest method =
                createUriRequest(actionURL, form.attr("method"));



        // form attr:
           // accept-charset="UTF-8"
           // enctype  Specifies how the form-data should be encoded when submitting it to the server (only for method="post") https://www.w3schools.com/tags/att_form_enctype.asp
           // name

//        formparams.add(new BasicNameValuePair(
//                cfg.getFormUsernameField(),
//                cfg.getCredentials().getUsername()));
//        formparams.add(new BasicNameValuePair(
//                cfg.getFormPasswordField(),
//                EncryptionUtil.decryptPassword(cfg.getCredentials())));

        return null;
    }

}
