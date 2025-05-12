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

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link SolrCommitter}.
 * </p>
 */
@Data
@Accessors(chain = true)
public class SolrCommitterConfig extends BaseBatchCommitterConfig {

    /** Default Solr ID field */
    public static final String DEFAULT_SOLR_ID_FIELD = "id";
    /** Default Solr content field */
    public static final String DEFAULT_SOLR_CONTENT_FIELD = "content";

    /**
     * The type of Solr client.
     */
    private SolrClientType solrClientType;

    /**
     * The endpoint URL used to connect to Solr (as per the client type
     * selected).
     */
    private String solrURL;

    /**
     * Whether to send an explicit commit request at the end of every
     * batch, or let the server auto-commit.
     */
    private boolean solrCommitDisabled;

    @JsonXmlMap(entryName = "param")
    private final Map<String, String> updateUrlParams = new HashMap<>();
    private final Credentials credentials = new Credentials();

    /**
     * The document field name containing the value to be stored
     * in Solr ID field. A {@code null} value (default) will use the
     * document reference instead of a document field.
     */
    private String sourceIdField;

    /**
     * The name of the Solr field where to store a document unique
     * identifier (sourceIdField).  Default is "id".
     */
    private String targetIdField = DEFAULT_SOLR_ID_FIELD;

    /**
     * The name of the Solr field where content will be stored. Default
     * is "content". A {@code null} value will disable storing the content.
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
