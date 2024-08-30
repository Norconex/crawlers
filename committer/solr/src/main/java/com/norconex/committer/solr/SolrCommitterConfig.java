/* Copyright 2019-2024 Norconex Inc.
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.committer.core.batch.BaseBatchCommitterConfig;
import com.norconex.commons.lang.bean.jackson.JsonXmlMap;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.time.DurationParser;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Commits documents to Apache Solr.
 * </p>
 *
 * <h3>Solr Client:</h3>
 * <p>
 * You can specify which type of
 * <a href="https://lucene.apache.org/solr/guide/8_1/using-solrj.html#types-of-solrclients">
 * Solr Client</a> to use.
 * The expected configuration value of "solrURL" is influenced
 * by the client type chosen.  Default client type is
 * <code>Http2SolrClient</code>. The clients are:
 * </p>
 * <dl>
 *   <dt>Http2SolrClient</dt>
 *     <dd>Default client. For direct access to a single Solr node. Ideal for
 *         local development or small setups. Expects a Solr URL.</dd>
 *   <dt>CloudSolrClient</dt>
 *     <dd>For use with a SolrCloud cluster. Expects a comma-separated list
 *         of Zookeeper hosts.</dd>
 *   <dt>LBHttp2SolrClient</dt>
 *     <dd>Simple load-balancing as an alternative to an external load balancer.
 *         Expects two or more Solr node URLs (comma-separated).</dd>
 *   <dt>ConcurrentUpdateHttp2SolrClient</dt>
 *     <dd>Optimized for mass upload on a single node.  Not best for queries.
 *         Expects a Solr URL.</dd>
 * </dl>
 *
 * <p>
 * The above client types are using the HTTP/2 protocol. They should be
 * favored over the following, which are using HTTP/1.x and are
 * considered <b>deprecated</b>:
 * </p>
 *
 * <dl>
 *   <dt>HttpSolrClient</dt>
 *     <dd>For direct access to a single Solr node. Ideal for
 *         local development. Expects a Solr URL.</dd>
 *   <dt>LBHttpSolrClient</dt>
 *     <dd>Simple load-balancing as an alternative to an external load balancer.
 *         Expects two or more Solr node URLs (comma-separated).</dd>
 *   <dt>ConcurrentUpdateSolrClient</dt>
 *     <dd>Optimized for mass upload on a single node.  Not best for queries.
 *         Expects a Solr URL.</dd>
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
 * {@nx.include com.norconex.committer.core.AbstractCommitter#restrictTo}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#fieldMappings}
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
 *   {@nx.include com.norconex.committer.core.batch.AbstractBatchCommitter#options}
 * </committer>
 * }
 *
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class SolrCommitterConfig extends BaseBatchCommitterConfig {

    /** Default Solr ID field */
    public static final String DEFAULT_SOLR_ID_FIELD = "id";
    /** Default Solr content field */
    public static final String DEFAULT_SOLR_CONTENT_FIELD = "content";

    /**
     * The type of Solr client.
     * @param solrClientType solr client type
     * @return Solr client type
     */
    private SolrClientType solrClientType;

    /**
     * The endpoint URL used to connect to Solr (as per the client type
     * selected).
     * @param solrURL solrURL
     * @return Solr URL
     */
    private String solrURL;

    /**
     * Whether to send an explicit commit request at the end of every
     * batch, or let the server auto-commit.
     * @param solrCommitDisabled <code>true</code> if sending Solr commit is
     *        disabled
     * @return <code>true</code> if disabling sending Solr commits.
     */
    private boolean solrCommitDisabled;

    @JsonXmlMap(entryName = "param")
    private final Map<String, String> updateUrlParams = new HashMap<>();
    private final Credentials credentials = new Credentials();

    /**
     * The document field name containing the value to be stored
     * in Solr ID field. A <code>null</code> value (default) will use the
     * document reference instead of a document field.
     * @param sourceIdField name of field containing id value,
     *        or <code>null</code>
     * @return name of field containing id value
     */
    private String sourceIdField;

    /**
     * The name of the Solr field where to store a document unique
     * identifier (sourceIdField).  Default is "id".
     * @param targetIdField name of Solr ID field
     * @return name of Solr ID field
     */
    private String targetIdField = DEFAULT_SOLR_ID_FIELD;

    /**
     * The name of the Solr field where content will be stored. Default
     * is "content". A <code>null</code> value will disable storing the content.
     * @param targetContentField target content field name
     * @return target content field name
     */
    private String targetContentField = DEFAULT_SOLR_CONTENT_FIELD;

    /**
     * Gets URL parameters to be added on Solr HTTP calls.
     * @return a map of parameter names and values
     */
    public Map<String, String> getUpdateUrlParams() {
        return Collections.unmodifiableMap(updateUrlParams);
    }

    /**
     * Sets URL parameters to be added on Solr HTTP calls.
     * @param updateUrlParams a map of parameter names and values
     */
    public void setUpdateUrlParams(Map<String, String> updateUrlParams) {
        CollectionUtil.setAll(this.updateUrlParams, updateUrlParams);
    }

    /**
     * Sets a URL parameter to be added on Solr HTTP calls.
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
    @JsonIgnore
    public Set<String> getUpdateUrlParamNames() {
        return updateUrlParams.keySet();
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
}
