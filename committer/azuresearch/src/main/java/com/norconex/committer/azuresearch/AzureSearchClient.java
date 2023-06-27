/* Copyright 2017-2023 Norconex Inc.
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
package com.norconex.committer.azuresearch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.collections4.KeyValue;
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.win.WinHttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterUtil;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.security.Credentials;

/**
 * <p>
 * Simple Microsoft Azure Search client.
 * </p>
 * @author Pascal Essiembre
 */
class AzureSearchClient {

    private static final Logger LOG =
            LoggerFactory.getLogger(AzureSearchClient.class);

    private final AzureSearchCommitterConfig config;
    private final CloseableHttpClient client;
    private final String restURL;

    public AzureSearchClient(AzureSearchCommitterConfig config) {
        super();
        this.config = Objects.requireNonNull(
                config, "'config' must not be null");
        if (StringUtils.isBlank(config.getEndpoint())) {
            throw new IllegalArgumentException("Endpoint is undefined.");
        }
        if (StringUtils.isBlank(config.getApiKey())) {
            throw new IllegalArgumentException("API admin key is undefined.");
        }
        if (StringUtils.isBlank(config.getIndexName())) {
            throw new IllegalArgumentException("Index name is undefined.");
        }

        String version = ObjectUtils.defaultIfNull(
                config.getApiVersion(),
                AzureSearchCommitterConfig.DEFAULT_API_VERSION);
        LOG.info("Azure Search API Version: {}", version);

        client = createHttpClient();
        restURL = StringUtils.stripEnd(config.getEndpoint(), "/")
                + "/indexes/" + config.getIndexName()
                + "/docs/index?api-version=" + version;
        LOG.info("Azure Search Doc Index URL: {}", restURL);
    }

    private CloseableHttpClient createHttpClient() {
        HttpClientBuilder builder;
        if (config.isUseWindowsAuth() && WinHttpClients.isWinAuthAvailable()) {
            builder = WinHttpClients.custom();
        } else {
            builder = HttpClientBuilder.create();
        }

        ProxySettings proxy = config.getProxySettings();
        if (proxy.isSet()) {
            HttpHost host = new HttpHost(
                    proxy.getScheme(),
                    proxy.getHost().getName(),
                    proxy.getHost().getPort());
            builder.setProxy(host);
            Credentials creds = proxy.getCredentials();
            if (creds.isSet()) {
                BasicCredentialsProvider cp = new BasicCredentialsProvider();
                cp.setCredentials(
                        new AuthScope(host, proxy.getRealm(), null),
                        new UsernamePasswordCredentials(
                                creds.getUsername(),
                                EncryptionUtil.decrypt(
                                        creds.getPassword(),
                                        creds.getPasswordKey()).toCharArray()));
                builder.setDefaultCredentialsProvider(cp);
            }
        }
        return builder.build();
    }

    public void post(Iterator<CommitterRequest> it) throws CommitterException {

        JSONArray jsonBatch = new JSONArray();
        try {
            while (it.hasNext()) {
                CommitterRequest req = it.next();

                KeyValue<String, String> docKeyField = resolveDocKeyField(req);
                if (docKeyField == null) {
                    continue;
                }

                if (req instanceof UpsertRequest upsert) {
                    jsonBatch.put(toJsonDocUpsert(upsert, docKeyField));
                } else if (req instanceof DeleteRequest) {
                    jsonBatch.put(toJsonDocDelete(docKeyField));
                } else {
                    throw new CommitterException("Unsupported request:" + req);
                }
            }
            if (jsonBatch.length() > 0) {
                uploadBatchToAzureSearch(jsonBatch);
            } else {
                LOG.warn("No documents were valid. Nothing committed.");
            }
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(
                    "Could not commit JSON batch to CloudSearch.", e);
        }
    }

    public void close() {
        client.close(CloseMode.GRACEFUL);
        LOG.info("Azure Search REST API Http Client closed.");
    }

    private KeyValue<String, String> resolveDocKeyField(CommitterRequest req)
            throws CommitterException {
        String keyField = Optional.ofNullable(trimToNull(
                config.getTargetKeyField())).orElse(
                        AzureSearchCommitterConfig.DEFAULT_AZURE_KEY_FIELD);
        String keyValue = CommitterUtil.extractSourceIdValue(
                req, config.getSourceKeyField());
        // Key value encoding
        if (!config.isDisableDocKeyEncoding()) {
            keyValue = Base64.encodeBase64URLSafeString(keyValue.getBytes());
        } else if (!validateDocumentKey(keyValue)) {
            return null;
        }
        return new DefaultKeyValue<>(keyField, keyValue);
    }

    private void uploadBatchToAzureSearch(JSONArray documentBatch)
            throws CommitterException {

        JSONObject json = new JSONObject();
        json.put("value", documentBatch);

        if (LOG.isTraceEnabled()) {
            LOG.trace("JSON POST:\n{}", StringUtils.trim(json.toString(2)));
        }

        StringEntity requestEntity = new StringEntity(
                json.toString(), ContentType.APPLICATION_JSON);
        HttpPost post = new HttpPost(restURL);
        post.addHeader("api-key", config.getApiKey());
        post.setEntity(requestEntity);
        try (ClassicHttpResponse response = client.executeOpen(null, post, null)) {
            handleResponse(response);
            LOG.info("Done sending {} upserts/deletes to Azure Search.",
                    documentBatch.length());
        } catch (IOException e) {
            throw new CommitterException(
                    "Could not commit JSON batch to Azure Search.", e);
        }
    }

    private void handleResponse(ClassicHttpResponse res)
            throws IOException, CommitterException {
        String responseAsString = "";
        HttpEntity entity = res.getEntity();
        if (entity != null) {
            try (InputStream is = entity.getContent()) {
                responseAsString = IOUtils.toString(entity.getContent(), UTF_8);
            }
        }
        int statusCode = res.getCode();
        if (statusCode != HttpStatus.SC_OK
                && statusCode != HttpStatus.SC_CREATED) {
            responseError("Invalid HTTP response: \"" + res.getReasonPhrase()
                    + "\". Azure Response: " + responseAsString);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Azure Search response status: {} - {}",
                        res.getCode(), res.getReasonPhrase());
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Azure Search response:\n{}", responseAsString);
            }
        }
    }

    private JSONObject toJsonDocUpsert(
            UpsertRequest req, KeyValue<String, String> docKeyField)
            throws CommitterException {

        CommitterUtil.applyTargetContent(req, config.getTargetContentField());

        // Build document
        Map<String, Object> docMap = new ListOrderedMap<>();
        docMap.put("@search.action", "upload");
        docMap.put(docKeyField.getKey(), docKeyField.getValue());
        for (Entry<String, List<String>> en : req.getMetadata().entrySet()) {
            String name = en.getKey();
            List<String> values = en.getValue();
            if (validateFieldName(name)) {
                toAzureValue(name, values).ifPresent(v -> docMap.put(name, v));
            }
        }

        return new JSONObject(docMap);
    }

    private JSONObject toJsonDocDelete(KeyValue<String, String> docKeyField) {
        Map<String, Object> docMap = new ListOrderedMap<>();
        docMap.put("@search.action", "delete");
        docMap.put(docKeyField.getKey(), docKeyField.getValue());
        return new JSONObject(docMap);
    }

    private Optional<Object> toAzureValue(String field, List<String> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        if (values.size() == 1 && !forceArray(field)) {
            return Optional.ofNullable(values.get(0));
        }
        return Optional.of(values);
    }
    private boolean forceArray(String field) {
        if (StringUtils.isBlank(config.getArrayFields())) {
            return false;
        }
        if (config.isArrayFieldsRegex()) {
            return Pattern.compile(
                    config.getArrayFields()).matcher(field).matches();
        }
        return ArrayUtils.contains(
                config.getArrayFields().split("\\s*,\\s*"), field);
    }
    private boolean validateFieldName(String field) throws CommitterException {
        if (field.startsWith("azureSearch")) {
            validationError("Document field cannot begin "
                    + "with \"azureSearch\": " + field);
            return false;
        }
        if (!field.matches("[A-Za-z0-9_]+")) {
            validationError("Document field cannot have "
                    + "one or more characters other than letters, "
                    + "numbers and underscores: " + field);
            return false;
        }
        if (field.length() > 128) {
            validationError("Document field cannot be "
                    + "longer than 128 characters: " + field);
            return false;
        }
        return true;
    }
    private boolean validateDocumentKey(String docId)
            throws CommitterException {
        if (docId.startsWith("_")) {
            validationError("Document key cannot start "
                    + "with an underscore character: " + docId);
            return false;
        }
        if (!docId.matches("[A-Za-z0-9_\\-=]+")) {
            validationError("Document key cannot have one or more "
                    + "characters other than letters, numbers, dashes, "
                    + "underscores, and equal signs: " + docId);
            return false;
        }
        return true;
    }

    private void validationError(String errorMsg) throws CommitterException {
        error(errorMsg, config.isIgnoreValidationErrors());
    }
    private void responseError(String errorMsg) throws CommitterException {
        error(errorMsg, config.isIgnoreResponseErrors());
    }
    private void error(String errorMsg, boolean ignoreErrors)
            throws CommitterException {
        if (ignoreErrors) {
            LOG.error(errorMsg);
        } else {
            throw new CommitterException(errorMsg);
        }
    }
}
