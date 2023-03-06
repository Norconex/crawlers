/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.crawler.web.fetch.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.replaceChars;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
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
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.fetch.impl.HttpAuthConfig;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.importer.doc.DocRecord;

/**
 * Utility methods for fetcher implementations using Apache HttpClient.
 * @since 3.0.0
 */
public final class ApacheHttpUtil {

    private static final Logger LOG =
            LoggerFactory.getLogger(ApacheHttpUtil.class);

    private ApacheHttpUtil() {
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
     * that can be set on the document {@link WebDocRecord}:
     * </p>
     * <ul>
     *   <li>Content type</li>
     *   <li>Content encoding</li>
     *   <li>ETag</li>
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
        WebDocRecord docRecord = (WebDocRecord) doc.getDocRecord();
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
                applyContentTypeAndCharset(value, docRecord);
            }

            // ETag
            if (HttpHeaders.ETAG.equalsIgnoreCase(name)
                    && StringUtils.isNotBlank(value)) {
                docRecord.setEtag(value);
            }

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
     * {@link DocRecord#setContentType(ContentType)} and
     * {@link DocRecord#setContentEncoding(String)}.
     * This method is automatically invoked by
     * {@link #applyResponseHeaders(HttpResponse, String, CrawlDoc)}
     * when encountering a content type header.
     * @param value value to parse and set.
     * @param docRecord document info
     */
    public static void applyContentTypeAndCharset(
            String value, DocRecord docRecord) {
        if (StringUtils.isBlank(value) || docRecord == null) {
            return;
        }
        // delegate parsing of content-type honoring various forms
        // https://tools.ietf.org/html/rfc7231#section-3.1.1
        org.apache.http.entity.ContentType apacheCT =
                org.apache.http.entity.ContentType.parse(value);

        // only overwrite object properties if not null
        ContentType ct = ContentType.valueOf(apacheCT.getMimeType());
        if (ct != null) {
            docRecord.setContentType(ct);
        }
        Charset charset = apacheCT.getCharset();
        if (charset != null) {
            docRecord.setContentEncoding(charset.toString());
        }
    }


    /**
     * Sets the <code>If-Modified-Since</code> HTTP request header based
     * on document cached last crawled date (if any).
     * @param request HTTP request
     * @param doc document
     */
    public static void setRequestIfModifiedSince(
            HttpRequest request, CrawlDoc doc) {
        if (doc.hasCache()) {
            ZonedDateTime zdt = doc.getCachedDocRecord().getCrawlDate();
            if (zdt != null) {
                request.addHeader(HttpHeaders.IF_MODIFIED_SINCE,
                        zdt.format(DateTimeFormatter.RFC_1123_DATE_TIME));
            }
        }
    }

    /**
     * Sets the ETag <code>If-None-Match</code> HTTP request header based
     * on document cached ETag value (if any).
     * @param request HTTP request
     * @param doc document
     */
    public static void setRequestIfNoneMatch(
            HttpRequest request, CrawlDoc doc) {
        if (doc.hasCache()) {
            WebDocRecord docRecord = (WebDocRecord) doc.getCachedDocRecord();
            if (docRecord.getEtag() != null) {
                request.addHeader(HttpHeaders.IF_NONE_MATCH, docRecord.getEtag());
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
        String m = firstNonBlank(method, "GET");
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
                    throws IOException, URISyntaxException {
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
                LOG.debug("Authentication response: {}", IOUtils.toString(
                        response.getEntity().getContent(),
                        StandardCharsets.UTF_8));
            }
        } finally {
            post.releaseConnection();
        }
    }

    private static void authLoginPage(
            HttpClient httpClient, HttpAuthConfig cfg)
                    throws IOException, URISyntaxException {

        LOG.info("Parsing, filing, and submitting login FORM at \"{}\" "
                + "(username={}; p"
                + "assword=*****)", cfg.getUrl(),
                cfg.getCredentials().getUsername());
        HttpGet get = new HttpGet(cfg.getUrl());
        try {
            HttpResponse formReadResponse =
                    httpClient.execute(get, (HttpContext) null);
            HttpEntity entity = formReadResponse.getEntity();
            if (entity == null) {
                LOG.error("Authentication URL returned no content. "
                        + "Status: {}.", formReadResponse.getStatusLine());
                return;
            }
            Document doc = Jsoup.parse(
                    entity.getContent(),
                    entity.getContentEncoding() != null
                            ? entity.getContentEncoding().getValue()
                            : null,
                    cfg.getUrl());
            HttpRequestBase authReq = formToRequest(doc, cfg);
            if (authReq != null) {
                try {
                    // execute auth request
                    HttpResponse formSubmitResponse =
                            httpClient.execute(authReq);
                    LOG.info("Authentication status: {}.",
                            formSubmitResponse.getStatusLine());
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Authentication response: {}",
                                IOUtils.toString(formSubmitResponse
                                        .getEntity().getContent(),
                                StandardCharsets.UTF_8));
                    }
                } finally {
                    authReq.releaseConnection();
                }
            }
            //TODO share some of auth code with authFormAction(...)
        } finally {
            get.releaseConnection();
        }




    }

    private static HttpRequestBase formToRequest(
            Document doc, HttpAuthConfig cfg) throws URISyntaxException {

        Element form = doc.selectFirst(cfg.getFormSelector());
        if (form == null) {
            LOG.error("Page has no login form: {}", cfg.getUrl());
            return null;
        }

        //--- Load/populate form ---

        Map<String, String> params = new ListOrderedMap<>();
        // Loop through each input and fill/overwrite matching
        // ones, else take as is.
        for (Element el: form.select("[name]")) {
            String name = el.attr("name");
            String value = el.val();
            if (StringUtils.isBlank(value)) {
                value = el.wholeText();
            }
            params.put(name, value);
        }

        // overwrite matching form params from configuration
        if (cfg.getFormUsernameField() != null) {
            params.put(cfg.getFormUsernameField(),
                    cfg.getCredentials().getUsername());
        }
        if (cfg.getFormPasswordField() != null) {
            params.put(cfg.getFormPasswordField(),
                    EncryptionUtil.decryptPassword(cfg.getCredentials()));
        }
        params.putAll(cfg.getFormParams());


        //--- Form params to HTTP request ---

        // If no "action", we assume self.
        String actionURL =
                firstNonBlank(form.attr("abs:action"), cfg.getUrl());
        // If no "method", we assume "GET".
        HttpRequestBase httpRequest =
                createUriRequest(actionURL, form.attr("method"));

        // Only for POST. https://www.w3schools.com/tags/att_form_enctype.asp
        if (httpRequest instanceof HttpPost) {
            HttpEntity entity;
            String enctype = form.attr("enctype");
            Charset charset = ObjectUtils.firstNonNull(
                    cfg.getFormCharset(),
                    isNotBlank(form.attr("accept-charset"))
                            ? Charset.forName(form.attr("accept-charset"))
                            : UTF_8);
            if ("multipart/form-data".equalsIgnoreCase(enctype)) {
                FormBodyPartBuilder fb = FormBodyPartBuilder.create();
                for (Entry<String, String> en : params.entrySet()) {
                    fb.addField(en.getKey(), en.getValue());
                }
                entity = MultipartEntityBuilder.create().addPart(
                        fb.build()).build();
            } else if ("text/plain".equalsIgnoreCase(enctype)) {
                StringBuilder b = new StringBuilder();
                for (Entry<String, String> en : params.entrySet()) {
                    b.append(replaceChars(en.getKey(), ' ', '+'));
                    b.append('=');
                    b.append(replaceChars(en.getValue(), ' ', '+'));
                    b.append('\n');
                }
                entity = new StringEntity(b.toString(), charset);
            } else {
                // defaults to: application/x-www-form-urlencoded
                entity = new UrlEncodedFormEntity(
                        toNameValuePairs(params), charset);
            }
            ((HttpPost) httpRequest).setEntity(entity);
        } else if (httpRequest instanceof HttpGet) {
            HttpGet get = (HttpGet) httpRequest;
            get.setURI(new URIBuilder(get.getURI())
                    .setParameters(toNameValuePairs(params))
                    .build());
        } else {
            LOG.error("Form method not spported: {}", httpRequest.getMethod());
            return null;
        }
        return httpRequest;
    }

    private static List<NameValuePair> toNameValuePairs(
            Map<String, String> map) {
        List<NameValuePair> pairs = new ArrayList<>();
        for (Entry<String, String> en : map.entrySet()) {
            pairs.add(new BasicNameValuePair(en.getKey(), en.getValue()));
        }
        return pairs;
    }
}
