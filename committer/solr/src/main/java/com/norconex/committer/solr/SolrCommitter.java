/* Copyright 2019-2023 Norconex Inc.
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

package com.norconex.committer.solr;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringExclude;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.CommitterUtil;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.io.IOUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Commits documents to Apache Solr.
 * </p>
 *
 * <h3>Solr Client:</h3>
 * <p>
 * As of 2.4.0, it is possible to specify which type of
 * <a href="https://lucene.apache.org/solr/guide/8_1/using-solrj.html#types-of-solrclients">
 * Solr Client</a> to use.
 * The expected configuration value of "solrURL" is influenced
 * by the client type chosen.  Default client type is
 * <code>HttpSolrClient</code>. The clients are:
 * </p>
 * <dl>
 *   <dt>HttpSolrClient</dt>
 *     <dd>For direct access to a single Solr node. Ideal for
 *         local development. Needs a Solr URL. Default client.</dd>
 *   <dt>LBHttpSolrClient</dt>
 *     <dd>Simple load-balancing as an alternative to an external load balancer.
 *         Needs two or more Solr node URLs (comma-separated).</dd>
 *   <dt>ConcurrentUpdateSolrClient</dt>
 *     <dd>Optimized for mass upload on a single node.  Not best for queries.
 *         Needs a Solr URL.</dd>
 *   <dt>CloudSolrClient</dt>
 *     <dd>For use with a SolrCloud cluster. Needs a comma-separated list
 *         of Zookeeper hosts.</dd>
 *   <dt>Http2SolrClient</dt>
 *     <dd>Same as HttpSolrClient but for HTTP/2 support. Marked as
 *         experimental by Apache.</dd>
 *   <dt>LBHttp2SolrClient</dt>
 *     <dd>Same as LBHttpSolrClient but for HTTP/2 support. Marked as
 *         experimental by Apache.</dd>
 *   <dt>ConcurrentUpdateHttp2SolrClient</dt>
 *     <dd>Same as LBHttpSolrClient but for HTTP/2 support. Marked as
 *         experimental by Apache.</dd>
 * </dl>
 *
 * <h3>Authentication</h3>
 * <p>
 * Basic authentication is supported for password-protected
 * Solr installations.
 * </p>
 *
 * {@nx.include com.norconex.commons.lang.security.Credentials#doc}
 *
 * {@nx.include com.norconex.committer.core3.AbstractCommitter#restrictTo}
 *
 * {@nx.include com.norconex.committer.core3.AbstractCommitter#fieldMappings}
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.solr.SolrCommitter">
 *   <solrClientType>
 *     (See class documentation for options. Default: HttpSolrClient.)
 *   </solrClientType>
 *   <solrURL>(URL to Solr)</solrURL>
 *   <solrUpdateURLParams>
 *     <param name="(parameter name)">(parameter value)</param>
 *     <!-- multiple param tags allowed -->
 *   </solrUpdateURLParams>
 *   <solrCommitDisabled>[false|true]</solrCommitDisabled>
 *
 *   <!-- Use the following if authentication is required. -->
 *   <credentials>
 *     {@nx.include com.norconex.commons.lang.security.Credentials@nx.xml.usage}
 *   </credentials>
 *
 *   <sourceIdField>
 *     (Optional document field name containing the value that will be stored
 *     in Solr target ID field. Default is the document reference.)
 *   </sourceIdField>
 *   <targetIdField>
 *     (Optional name of Solr field where to store a document unique
 *     identifier (sourceIdField).  If not specified, default is "id".)
 *   </targetIdField>
 *   <targetContentField>
 *     (Optional Solr field name to store document content/body.
 *     Default is "content".)
 *   </targetContentField>
 *
 *   {@nx.include com.norconex.committer.core3.batch.AbstractBatchCommitter#options}
 * </committer>
 * }
 *
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * @author Pascal Essiembre
 * @author Harinder Hanjan
 */

public class SolrCommitter extends AbstractBatchCommitter {

	private static final Logger LOG =
            LoggerFactory.getLogger(SolrCommitter.class);

    /** Default Solr ID field */
    public static final String DEFAULT_SOLR_ID_FIELD = "id";
    /** Default Solr content field */
    public static final String DEFAULT_SOLR_CONTENT_FIELD = "content";

    private SolrClientType solrClientType;
    private String solrURL;
    private boolean solrCommitDisabled;
    private final Map<String, String> updateUrlParams = new HashMap<>();
    private final Credentials credentials = new Credentials();

    private String sourceIdField;
    private String targetIdField = DEFAULT_SOLR_ID_FIELD;
    private String targetContentField = DEFAULT_SOLR_CONTENT_FIELD;

    @ToStringExclude
    @HashCodeExclude
    @EqualsExclude
    private SolrClient solrClient;

    /**
     * Constructor.
     */
    public SolrCommitter() {
        super();
    }

    /**
     * Gets the Solr client type.
     * @return solr client type
     */
    public SolrClientType getSolrClientType() {
        return solrClientType;
    }
    /**
     * Sets the Solr client type.
     * @param solrClientType solr client type
     */
    public void setSolrClientType(SolrClientType solrClientType) {
        this.solrClientType = solrClientType;
    }

    /**
     * Gets the Solr URL.
     * @return Solr URL
     */
    public String getSolrURL() {
        return solrURL;
    }
    /**
     * Sets the Solr URL.
     * @param solrURL solrURL
     */
    public void setSolrURL(String solrURL) {
        this.solrURL = solrURL;
    }

    /**
     * Sets URL parameters to be added on Solr HTTP calls.
     * @param name parameter name
     * @param value parameter value
     */
    public void setUpdateUrlParam(String name, String value) {
        updateUrlParams.put(name, value);
    }
    /**
     * Gets a URL parameter value by its parameter name.
     * @param name parameter name
     * @return parameter value
     */
    public String getUpdateUrlParam(String name) {
        return updateUrlParams.get(name);
    }
    /**
     * Gets the update URL parameter names.
     * @return parameter names
     */
    public Set<String> getUpdateUrlParamNames() {
        return updateUrlParams.keySet();
    }

    /**
     * Sets whether to send an explicit commit request at the end of every
     * batch, or let the server auto-commit.
     * @param solrCommitDisabled <code>true</code> if sending Solr commit is
     *        disabled
     */
    public void setSolrCommitDisabled(boolean solrCommitDisabled) {
        this.solrCommitDisabled = solrCommitDisabled;
    }
    /**
     * Gets whether to send an explicit commit request at the end of every
     * batch, or let the server auto-commit.
     * @return <code>true</code> if sending Solr commit is disabled.
     */
    public boolean isSolrCommitDisabled() {
        return solrCommitDisabled;
    }

    /**
     * Gets Solr authentication credentials.
     * @return credentials
     */
    public Credentials getCredentials() {
        return credentials;
    }
    /**
     * Sets Solr authentication credentials.
     * @param credentials the credentials
     */
    public void setCredentials(Credentials credentials) {
        this.credentials.copyFrom(credentials);
    }

    /**
     * Gets the name of the Solr field where content will be stored. Default
     * is "content".
     * @return field name
     */
    public String getTargetContentField() {
        return targetContentField;
    }
    /**
     * Sets the name of the Solr field where content will be stored.
     * Specifying a <code>null</code> value will disable storing the content.
     * @param targetContentField field name
     */
    public void setTargetContentField(String targetContentField) {
        this.targetContentField = targetContentField;
    }

    /**
     * Gets the document field name containing the value to be stored
     * in Solr ID field. Default is not a field, but rather
     * the document reference.
     * @return name of field containing id value
     */
    public String getSourceIdField() {
        return sourceIdField;
    }
    /**
     * Sets the document field name containing the value to be stored
     * in Solr ID field. Set <code>null</code> to use the
     * document reference instead of a field (default).
     * @param sourceIdField name of field containing id value,
     *        or <code>null</code>
     */
    public void setSourceIdField(String sourceIdField) {
        this.sourceIdField = sourceIdField;
    }

    /**
     * Gets the name of the Solr field where to store a document unique
     * identifier (sourceIdField).  Default is "id".
     * @return name of Solr ID field
     */
    public String getTargetIdField() {
        return targetIdField;
    }
    /**
     * Sets the name of the Solr field where to store a document unique
     * identifier (sourceIdField).  If not specified, default is "id".
     * @param targetIdField name of Solr ID field
     */
    public void setTargetIdField(String targetIdField) {
        this.targetIdField = targetIdField;
    }

    @Override
    protected void initBatchCommitter() throws CommitterException {
        solrClient = ObjectUtils.defaultIfNull(solrClientType,
                SolrClientType.HTTP).create(solrURL);
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {

        // Add to request all operations in batch, and force a commit
        // whenever we do a "delete" after an "add" to eliminate the
        // risk of the delete being a no-op since added documents are
        // not visible until committed (thus nothing to delete).

        //MAYBE: before a delete, check if the same reference was previously
        //added before forcing a commit if any additions occurred.

        int docCount = 0;
        try {
            final UpdateRequest solrBatchRequest = new UpdateRequest();
            boolean previousWasAddition = false;
            while (it.hasNext()) {
                CommitterRequest r = it.next();
                if (r instanceof UpsertRequest upsert) {
                    addSolrUpsertRequest(solrBatchRequest, upsert);
                    previousWasAddition = true;
                } else if (r instanceof DeleteRequest delete) {
                    if (previousWasAddition) {
                        pushSolrRequest(solrBatchRequest);
                    }
                    addSolrDeleteRequest(solrBatchRequest, delete);
                    previousWasAddition = false;
                } else {
                    throw new CommitterException("Unsupported operation:" + r);
                }
                docCount++;
            }

            pushSolrRequest(solrBatchRequest);
            LOG.info("Sent {} committer operations to Solr.", docCount);

        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(
                    "Cannot push document batch to Solr.", e);
        }
    }

    @Override
    protected void closeBatchCommitter() throws CommitterException {
        IOUtil.closeQuietly(solrClient);
        solrClient = null;
        LOG.info("SolrClient closed.");
    }

    protected void pushSolrRequest(UpdateRequest solrBatchRequest)
            throws SolrServerException, IOException, CommitterException {

        if (credentials.isSet()) {
            solrBatchRequest.setBasicAuthCredentials(
                    credentials.getUsername(),
                    EncryptionUtil.decryptPassword(credentials));
        }
        for (Entry<String, String> entry : updateUrlParams.entrySet()) {
            solrBatchRequest.setParam(entry.getKey(), entry.getValue());
        }

        handleResponse(solrBatchRequest.process(solrClient));
        if (!isSolrCommitDisabled()) {
            handleResponse(solrBatchRequest.commit(solrClient, null));
        }
        solrBatchRequest.clear();
    }

    protected void addSolrUpsertRequest(
            UpdateRequest solrBatchRequest, UpsertRequest committerRequest)
                    throws CommitterException {

        CommitterUtil.applyTargetId(
                committerRequest, sourceIdField, targetIdField);
        CommitterUtil.applyTargetContent(committerRequest, targetContentField);
        solrBatchRequest.add(buildSolrDocument(committerRequest.getMetadata()));
    }
    protected void addSolrDeleteRequest(
            UpdateRequest solrBatchRequest, DeleteRequest committerRequest) {
        CommitterUtil.applyTargetId(
                committerRequest, sourceIdField, targetIdField);
        solrBatchRequest.deleteById(committerRequest.getReference());
    }
    protected SolrInputDocument buildSolrDocument(Properties fields) {
        SolrInputDocument doc = new SolrInputDocument();
        for (String key : fields.keySet()) {
            List<String> values = fields.getStrings(key);
            for (String value : values) {
                doc.addField(key, value);
            }
        }
        return doc;
    }

    private void handleResponse(UpdateResponse response)
            throws CommitterException {
        handleResponse(response.getResponse());
    }
    private void handleResponse(NamedList<Object> response)
            throws CommitterException {
        @SuppressWarnings("unchecked")
        NamedList<Object> headers =
                (NamedList<Object>) response.get("responseHeader");
        if (headers == null) {
            throw new CommitterException(
                    "No response headers obtained from Solr request. "
                  + "Response: " + response);
        }
        String status = Objects.toString(headers.get("status"), null);
        if (!"0".equals(status)) {
            throw new CommitterException(
                    "Invalid Solr response status: " + status);
        }
    }

    @Override
    protected void loadBatchCommitterFromXML(XML xml) {
        setSolrClientType(xml.getEnum("solrClientType",
                SolrClientType.class, getSolrClientType()));
        setSolrURL(xml.getString("solrURL", getSolrURL()));
        setSolrCommitDisabled(xml.getBoolean(
                "solrCommitDisabled", isSolrCommitDisabled()));

        List<XML> paramsXML = xml.getXMLList("solrUpdateURLParams/param");
        if (!paramsXML.isEmpty()) {
            updateUrlParams.clear();
            paramsXML.forEach(p -> setUpdateUrlParam(
                    p.getString("@name"), p.getString(".")));
        }

        xml.ifXML("credentials", x -> x.populate(credentials));

        setSourceIdField(xml.getString("sourceIdField", getSourceIdField()));
        setTargetIdField(xml.getString("targetIdField", getTargetIdField()));
        setTargetContentField(xml.getString(
                "targetContentField", getTargetContentField()));
    }

    @Override
    protected void saveBatchCommitterToXML(XML xml) {
        xml.addElement("solrClientType", getSolrClientType());
        xml.addElement("solrURL", solrURL);

        if (!updateUrlParams.isEmpty()) {
            XML paramsXML = xml.addElement("solrUpdateURLParams");
            updateUrlParams.forEach((k, v) -> {
                paramsXML.addElement("param", v).setAttribute("name", k);
            });
        }

        xml.addElement("solrCommitDisabled", isSolrCommitDisabled());
        credentials.saveToXML(xml.addElement("credentials"));
        xml.addElement("sourceIdField", getSourceIdField());
        xml.addElement("targetIdField", getTargetIdField());
        xml.addElement("targetContentField", getTargetContentField());
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
