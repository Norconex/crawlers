/* Copyright 2013-2023 Norconex Inc.
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
package com.norconex.committer.elasticsearch;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.Node;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClient.FailureListener;
import org.elasticsearch.client.sniff.ElasticsearchNodesSniffer;
import org.elasticsearch.client.sniff.NodesSniffer;
import org.elasticsearch.client.sniff.Sniffer;
import org.json.JSONObject;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.CommitterUtil;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.io.IOUtil;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.text.StringUtil;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Commits documents to Elasticsearch. This committer
 * relies on Elasticsearch REST API.
 * </p>
 *
 * <h3>"_id" field</h3>
 * <p>
 * Elasticsearch expects a field named "_id" that uniquely identifies
 * each documents.  You can provide that field yourself in documents
 * you submit.  If you do not specify an "_id" field, this committer
 * will create one for you, using the document reference as the identifier
 * value.
 * </p>
 *
 * <h3>"content" field</h3>
 * <p>
 * By default the "body" of a document is read as an input stream
 * and stored in a "content" field.  You can change that target field name
 * with {@link #setTargetContentField(String)}.  If you set the target
 * content field to <code>null</code>, it will effectively skip storing
 * the content stream.
 * </p>
 *
 * <h3>Dots (.) in field names</h3>
 * <p>
 * Your Elasticsearch installation may consider dots in field names
 * to be representing "objects", which may not always be what you want.
 * If having dots is causing you issues, make sure not to submit fields
 * with dots, or use {@link #setDotReplacement(String)} to replace dots
 * with a character of your choice (e.g., underscore).
 * If your dot represents a nested object, keep reading.
 * </p>
 *
 * <h3>JSON Objects</h3>
 * <p>
 * It is possible to provide a regular expression
 * that will identify one or more fields containing a JSON object rather
 * than a regular string ({@link #setJsonFieldsPattern(String)}). For example,
 * this is a useful way to store nested objects.  While very flexible,
 * it can be challenging to come up with the JSON structure.  You may
 * want to consider custom code.
 * For this to work properly, make sure you define your Elasticsearch
 * field mappings on your index beforehand.
 * </p>
 *
 * <h3>Elasticsearch ID limitations:</h3>
 * <p>
 * As of this writing, Elasticsearch 5 or higher have a 512 bytes
 * limitation on its "_id" field.
 * By default, an error (from Elasticsearch) will result from trying to submit
 * documents with an invalid ID. You can get around this by
 * setting {@link #setFixBadIds(boolean)} to <code>true</code>.  It will
 * truncate references that are too long and append a hash code to it
 * representing the truncated part. This approach is not 100%
 * collision-free (uniqueness), but it should safely cover the vast
 * majority of cases.
 * </p>
 *
 * <h3>Type Name</h3>
 * <p>
 * As of Elasticsearch 7.0, the index type has been deprecated.
 * If you are using Elasticsearch 7.0 or higher, do not configure the
 * <code>typeName</code>. Doing so may cause errors.
 * The <code>typeName</code> is available only for backward compatibility
 * for those using this Committer with older versions of Elasticsearch.
 * </p>
 *
 * <h3>Authentication</h3>
 * <p>
 * Basic authentication is supported for password-protected clusters.
 * </p>
 *
 * {@nx.include com.norconex.commons.lang.security.Credentials#doc}
 *
 * <h3>Timeouts</h3>
 * <p>
 * You can specify timeout values for when this committer sends documents
 * to Elasticsearch.
 * </p>
 *
 * {@nx.include com.norconex.committer.core3.AbstractCommitter#restrictTo}
 *
 * {@nx.include com.norconex.committer.core3.AbstractCommitter#fieldMappings}
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.elasticsearch.ElasticsearchCommitter">
 *   <nodes>
 *     (Comma-separated list of Elasticsearch node URLs.
 *     Defaults to http://localhost:9200)
 *   </nodes>
 *   <indexName>(Name of the index to use)</indexName>
 *   <typeName>
 *     (Name of the type to use. Deprecated since Elasticsearch v7.)
 *   </typeName>
 *   <ignoreResponseErrors>[false|true]</ignoreResponseErrors>
 *   <discoverNodes>[false|true]</discoverNodes>
 *   <dotReplacement>
 *     (Optional value replacing dots in field names)
 *   </dotReplacement>
 *   <jsonFieldsPattern>
 *     (Optional regular expression to identify fields containing JSON
 *     objects instead of regular strings)
 *   </jsonFieldsPattern>
 *   <connectionTimeout>(milliseconds)</connectionTimeout>
 *   <socketTimeout>(milliseconds)</socketTimeout>
 *   <fixBadIds>
 *     [false|true](Forces references to fit into Elasticsearch _id field.)
 *   </fixBadIds>
 *
 *   <!-- Use the following if authentication is required. -->
 *   <credentials>
 *     {@nx.include com.norconex.commons.lang.security.Credentials@nx.xml.usage}
 *   </credentials>
 *
 *   <sourceIdField>
 *     (Optional document field name containing the value that will be stored
 *     in Elasticsearch "_id" field. Default is the document reference.)
 *   </sourceIdField>
 *   <targetContentField>
 *     (Optional Elasticsearch field name to store the document
 *     content/body. Default is "content".)
 *   </targetContentField>
 *
 *   {@nx.include com.norconex.committer.core3.batch.AbstractBatchCommitter#options}
 * </committer>
 * }
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * {@nx.xml.example
 * <committer class="com.norconex.committer.elasticsearch.ElasticsearchCommitter">
 *   <indexName>some_index</indexName>
 * </committer>
 * }
 *
 * <p>
 * The above example uses the minimum required settings, on the local host.
 * </p>
 * @author Pascal Essiembre
 */
@SuppressWarnings("javadoc")
@Data
@Slf4j
public class ElasticsearchCommitter extends AbstractBatchCommitter {

    public static final String ELASTICSEARCH_ID_FIELD = "_id";
    public static final String DEFAULT_ELASTICSEARCH_CONTENT_FIELD = "content";
    public static final String DEFAULT_NODE = "http://localhost:9200";
    public static final Duration DEFAULT_CONNECTION_TIMEOUT =
            Duration.ofSeconds(1);
    public static final Duration DEFAULT_SOCKET_TIMEOUT =
            Duration.ofSeconds(30);

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private RestClient client;
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Sniffer sniffer;

    private final List<String> nodes = new ArrayList<>(List.of(DEFAULT_NODE));

    /**
     * The Elasticsearch index name.
     * @param indexName the index name
     * @return index name
     */
    private String indexName;

    /**
     * The type name. Type name is deprecated if you
     * are using Elasticsearch 7.0 or higher and should be <code>null</code>.
     * @param typeName type name
     * @return type name
     */
    private String typeName;

    /**
     * Whether to ignore response errors.  By default, an exception is
     * thrown if the Elasticsearch response contains an error.
     * When <code>true</code> the errors are logged instead.
     * @param ignoreResponseErrors <code>true</code> when ignoring response
     *        errors
     * @return <code>true</code> when ignoring response errors
     */
    private boolean ignoreResponseErrors;

    /**
     * Whether automatic discovery of Elasticsearch cluster nodes should be
     * enabled.
     * @param discoverNodes <code>true</code> if enabled
     * @return <code>true</code> if enabled
     */
    private boolean discoverNodes;

    private final Credentials credentials = new Credentials();

    /**
     * The character used to replace dots in field names.
     * Default is <code>null</code> (does not replace dots).
     * @param dotReplacement replacement character or <code>null</code>
     * @return replacement character or <code>null</code>
     */
    private String dotReplacement;

    /**
     * The regular expression matching fields that contains a JSON
     * object for its value (as opposed to a regular string).
     * Default is <code>null</code>.
     * @param jsonFieldsPattern regular expression
     * @return regular expression
     */
    private String jsonFieldsPattern;

    /**
     * Elasticsearch connection timeout.
     * @param connectionTimeout connection timeout duration
     * @return connection duration
     */
    @NonNull
    private Duration connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;

    /**
     * Elasticsearch socket timeout.
     * @param socketTimeout socket timeout duration
     * @return socket timeout duration
     */
    @NonNull
    private Duration socketTimeout = DEFAULT_SOCKET_TIMEOUT;

    /**
     * Whether to fix IDs that are too long for Elasticsearch
     * ID limitation (512 bytes max). If <code>true</code>,
     * long IDs will be truncated and a hash code representing the
     * truncated part will be appended.
     * @param fixBadIds <code>true</code> to fix IDs that are too long
     * @return <code>true</code> to fix IDs that are too long
     */
    private boolean fixBadIds;

    /**
     * The document field name containing the value to be stored
     * in Elasticsearch "_id" field. Set to <code>null</code> to use the
     * document reference instead of a field (default).
     * @param sourceIdField name of source document field containing id value,
     *        or <code>null</code>
     * @return name of field containing id value
     */
    private String sourceIdField;

    /**
     * The name of the Elasticsearch field where content will be stored.
     * Default is "content". A <code>null</code> value disables storing
     * the content.
     * @param targetContentField Elasticsearch content field name
     * @return Elasticsearch content field name
     */
    private String targetContentField = DEFAULT_ELASTICSEARCH_CONTENT_FIELD;

    /**
     * Gets an unmodifiable list of Elasticsearch cluster node URLs.
     * Defaults to "http://localhost:9200".
     * @return Elasticsearch nodes
     */
    public List<String> getNodes() {
        return Collections.unmodifiableList(nodes);
    }
    /**
     * Sets cluster node URLs.
     * Node URLs with no port are assumed to be using port 80.
     * @param nodes Elasticsearch cluster nodes
     */
    public void setNodes(String... nodes) {
        CollectionUtil.setAll(this.nodes, nodes);
    }
    /**
     * Sets cluster node URLs.
     * Node URLs with no port are assumed to be using port 80.
     * @param nodes Elasticsearch cluster nodes
     */
    public void setNodes(List<String> nodes) {
        CollectionUtil.setAll(this.nodes, nodes);
    }

    /**
     * Gets Elasticsearch authentication credentials.
     * @return credentials
     */
    public Credentials getCredentials() {
        return credentials;
    }
    /**
     * Sets Elasticsearch authentication credentials.
     * @param credentials the credentials
     */
    public void setCredentials(Credentials credentials) {
        this.credentials.copyFrom(credentials);
    }

    @Override
    protected void initBatchCommitter() throws CommitterException {
        if (StringUtils.isBlank(getIndexName())) {
            throw new CommitterException("Index name is undefined.");
        }
        client = createRestClient();
        if (isDiscoverNodes()) {
            sniffer = createSniffer(client);
        }
    }

    private String extractId(CommitterRequest req) throws CommitterException {
        return fixBadIdValue(
                CommitterUtil.extractSourceIdValue(req, sourceIdField));
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {

        var json = new StringBuilder();

        var docCount = 0;
        try {
            while (it.hasNext()) {
                var req = it.next();
                if (req instanceof UpsertRequest upsert) {
                    appendUpsertRequest(json, upsert);
                } else if (req instanceof DeleteRequest delete) {
                    appendDeleteRequest(json, delete);
                } else {
                    throw new CommitterException("Unsupported request: " + req);
                }
                docCount++;
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("JSON POST:\n{}", StringUtils.trim(json.toString()));
            }

            var request = new Request("POST", "/_bulk");
            request.setJsonEntity(json.toString());
            var response = client.performRequest(request);
            handleResponse(response);
            LOG.info("Sent {} commit operations to Elasticsearch.", docCount);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(
                    "Could not commit JSON batch to Elasticsearch.", e);
        }
    }

    @Override
    protected void closeBatchCommitter() throws CommitterException {
        IOUtil.closeQuietly(sniffer);
        IOUtil.closeQuietly(client);
        client = null;
        sniffer = null;
        LOG.info("Elasticsearch RestClient closed.");
    }


    private void handleResponse(Response response)
            throws IOException, CommitterException {
        var respEntity = response.getEntity();
        if (respEntity != null) {
            var responseAsString = IOUtils.toString(
                    respEntity.getContent(), StandardCharsets.UTF_8);
            if (LOG.isTraceEnabled()) {
                LOG.trace("Elasticsearch response:\n{}", responseAsString);
            }

            // We have no need to parse the JSON if successful
            // (saving on the parsing). We'll do it on errors only
            // to filter out successful ones and report only the errors
            if (StringUtils.substring(
                    responseAsString, 0, 100).contains("\"errors\":true")) {
                var error = extractResponseErrors(responseAsString);
                if (!ignoreResponseErrors) {
                    throw new CommitterException(error);
                }
                LOG.error(error);
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Elasticsearch response status: {}",
                    response.getStatusLine());
        }
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new CommitterException(
                  "Invalid HTTP response: " + response.getStatusLine());
        }
    }

    private String extractResponseErrors(String response) {
        var error = new StringBuilder();
        var json = new JSONObject(response);
        var items = json.getJSONArray("items");

        for (var i = 0; i < items.length(); i++) {
            var index = items.getJSONObject(i).getJSONObject("index");
            if (index.has("error")) {
                if (error.length() > 0) {
                    error.append(",\n");
                }
                error.append(index.toString(4));
            }
        }
        error.append(']');
        error.insert(0, "Elasticsearch returned one or more errors:\n[");
        return error.toString();
    }

    private void appendUpsertRequest(StringBuilder json, UpsertRequest req)
            throws CommitterException {

        CommitterUtil.applyTargetContent(req, targetContentField);

        json.append("{\"index\":{");
        append(json, "_index", getIndexName());
        if (StringUtils.isNotBlank(getTypeName())) {
            append(json.append(','), "_type", getTypeName());
        }
        append(json.append(','), ELASTICSEARCH_ID_FIELD, extractId(req));
        json.append("}}\n{");
        var first = true;
        for (Entry<String, List<String>> entry : req.getMetadata().entrySet()) {
            var field = entry.getKey();
            field = StringUtils.replace(field, ".", dotReplacement);
            // Do not store _id as a field since it is passed above already.
            if (ELASTICSEARCH_ID_FIELD.equals(field)) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            append(json, field, entry.getValue());
            first = false;
        }
        json.append("}\n");
    }

    private void appendDeleteRequest(StringBuilder json, DeleteRequest req)
            throws CommitterException {
        json.append("{\"delete\":{");
        append(json, "_index", getIndexName());
        if (StringUtils.isNotBlank(getTypeName())) {
            append(json.append(','), "_type", getTypeName());
        }
        append(json.append(','), ELASTICSEARCH_ID_FIELD, extractId(req));
        json.append("}}\n");
    }

    private void append(StringBuilder json, String field, List<String> values) {
        if (values.size() == 1) {
            append(json, field, values.get(0));
            return;
        }

        json.append('"')
            .append(StringEscapeUtils.escapeJson(field))
            .append("\":[");
        
        for (String value : values) {
            appendValue(json, field, value);
            json.append(',');
        }
        
        json.deleteCharAt(json.length()-1);
        json.append(']');
    }

    private void append(StringBuilder json, String field, String value) {
        json.append('"')
            .append(StringEscapeUtils.escapeJson(field))
            .append("\":");
        appendValue(json, field, value);
    }

    private void appendValue(StringBuilder json, String field, String value) {
        if (getJsonFieldsPattern() != null
                && getJsonFieldsPattern().matches(field)) {
            json.append(value);
        } else {
            json.append('"')
                .append(StringEscapeUtils.escapeJson(value))
                .append("\"");
        }
    }

    private String fixBadIdValue(String value) throws CommitterException {
        if (StringUtils.isBlank(value)) {
            throw new CommitterException("Document id cannot be empty.");
        }
        if (fixBadIds && value.getBytes(StandardCharsets.UTF_8).length > 512) {
            String v;
            try {
                v = StringUtil.truncateBytesWithHash(
                        value, StandardCharsets.UTF_8, 512, "!");
            } catch (CharacterCodingException e) {
                LOG.error("""
                    Bad id detected (too long), but could not be\s\
                    truncated properly by byte size. Will truncate\s\
                    based on characters size instead, which may not\s\
                    work on IDs containing multi-byte characters.""");
                v = StringUtil.truncateWithHash(value, 512, "!");
            }
            if (LOG.isDebugEnabled() && !value.equals(v)) {
                LOG.debug("Fixed document id from \"{}\" to \"{}\".", value, v);
            }
            return v;
        }
        return value;
    }


    protected RestClient createRestClient() {
        var elasticHosts = getNodes();
        var httpHosts = new HttpHost[elasticHosts.size()];
        for (var i = 0; i < elasticHosts.size(); i++) {
            httpHosts[i] = HttpHost.create(elasticHosts.get(i));
        }

        var builder = RestClient.builder(httpHosts);
        builder.setFailureListener(new FailureListener() {
            @Override
            public void onFailure(Node node) {
                LOG.error("Failure occured on node: \"{}\". Check node logs.",
                        node.getName());
            }
        });
        builder.setRequestConfigCallback(rcb -> rcb
                .setConnectTimeout((int) connectionTimeout.toMillis())
                .setSocketTimeout((int) socketTimeout.toMillis()));

        if (credentials.isSet()) {
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    AuthScope.ANY, new UsernamePasswordCredentials(
                            credentials.getUsername(), EncryptionUtil.decrypt(
                                    credentials.getPassword(),
                                    credentials.getPasswordKey())));
            builder.setHttpClientConfigCallback(
                    b -> b.setDefaultCredentialsProvider(credsProvider));
        }
        return builder.build();
    }

    protected Sniffer createSniffer(RestClient client) {
        // here we assume a cluster is either all https, or all https (no mix).
        if (!nodes.isEmpty() && nodes.get(0).startsWith("https:")) {
            NodesSniffer nodesSniffer = new ElasticsearchNodesSniffer(client,
                    ElasticsearchNodesSniffer.DEFAULT_SNIFF_REQUEST_TIMEOUT,
                    ElasticsearchNodesSniffer.Scheme.HTTPS);
            return Sniffer.builder(
                    client).setNodesSniffer(nodesSniffer).build();
        }
        return Sniffer.builder(client).build();
    }

    @Override
    protected void saveBatchCommitterToXML(XML xml) {
        xml.addDelimitedElementList("nodes", getNodes());
        xml.addElement("indexName", getIndexName());
        xml.addElement("typeName", getTypeName());
        xml.addElement("ignoreResponseErrors", isIgnoreResponseErrors());
        xml.addElement("discoverNodes", isDiscoverNodes());
        credentials.saveToXML(xml.addElement("credentials"));
        xml.addElement("dotReplacement", getDotReplacement());
        xml.addElement("jsonFieldsPattern", getJsonFieldsPattern());
        xml.addElement("connectionTimeout", getConnectionTimeout());
        xml.addElement("socketTimeout", getSocketTimeout());
        xml.addElement("fixBadIds", isFixBadIds());
        xml.addElement("sourceIdField", getSourceIdField());
        xml.addElement("targetContentField", getTargetContentField());
    }
    @Override
    protected void loadBatchCommitterFromXML(XML xml) {
        setNodes(xml.getDelimitedStringList("nodes"));
        setIndexName(xml.getString("indexName", getIndexName()));
        setTypeName(xml.getString("typeName", getTypeName()));
        setIgnoreResponseErrors(xml.getBoolean(
                "ignoreResponseErrors", isIgnoreResponseErrors()));
        setDiscoverNodes(xml.getBoolean("discoverNodes", isDiscoverNodes()));
        xml.ifXML("credentials", x -> x.populate(credentials));
        setDotReplacement(xml.getString("dotReplacement", getDotReplacement()));
        setJsonFieldsPattern(
                xml.getString("jsonFieldsPattern", getJsonFieldsPattern()));
        setConnectionTimeout(xml.getDuration(
                "connectionTimeout", getConnectionTimeout()));
        setSocketTimeout(xml.getDuration("socketTimeout", getSocketTimeout()));
        setFixBadIds(xml.getBoolean("fixBadIds", isFixBadIds()));
        setSourceIdField(xml.getString("sourceIdField", getSourceIdField()));
        setTargetContentField(xml.getString(
                "targetContentField", getTargetContentField()));
    }
}
