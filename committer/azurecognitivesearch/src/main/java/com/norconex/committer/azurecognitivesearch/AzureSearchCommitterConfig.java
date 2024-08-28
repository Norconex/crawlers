/* Copyright 2021-2024 Norconex Inc.
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
package com.norconex.committer.azurecognitivesearch;

import java.io.Serializable;

import com.norconex.committer.core.batch.BaseBatchCommitterConfig;
import com.norconex.commons.lang.net.ProxySettings;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * <p>
 * Azure Search Committer configuration.
 * </p>
 * @author Pascal Essiembre
 */
@Data
@Accessors(chain = true)
@SuppressWarnings("javadoc")
public class AzureSearchCommitterConfig
        extends BaseBatchCommitterConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default Azure Search API version */
    public static final String DEFAULT_API_VERSION = "2020-06-30";
    /** Default Azure Search document key field */
    public static final String DEFAULT_AZURE_KEY_FIELD = "id";
    /** Default Azure Search content field */
    public static final String DEFAULT_AZURE_CONTENT_FIELD = "content";

    /**
     * Gets the Azure Search endpoint
     * (https://[service name].search.windows.net).
     * @param endpoint Azure Search endpoint
     * @return Azure Search endpoint
     */
    private String endpoint;

    /**
     * The Azure API version. Default is {@link #DEFAULT_API_VERSION}.
     * @return the Azure API version
     * @param apiVersion Azure API version
     */
    private String apiVersion = DEFAULT_API_VERSION;

    /**
     * The Azure API admin key.
     * @param apiKey Azure API admin key
     * @return Azure API admin key
     */
    private String apiKey;

    /**
     * The index name.
     * @param indexName the index name
     * @return index name
     */
    private String indexName;

    /**
     * Whether to disable document reference encoding. By default, references
     * are encoded using a URL-safe Base64 encoding.  When <code>true</code>,
     * document references will be sent as is if they pass validation.
     * @param disableDocKeyEncoding <code>true</code> if disabling
     *        reference encoding
     * @return <code>true</code> if disabling reference encoding
     */
    private boolean disableDocKeyEncoding;

    /**
     * Whether to ignore validation errors.  By default, an exception is
     * thrown if a document contains a field that Azure Search will reject.
     * When <code>true</code> the validation errors are logged
     * instead and the faulty field or document is not committed.
     * @param ignoreValidationErrors <code>true</code> when ignoring validation
     *        errors
     * @return <code>true</code> when ignoring validation errors
     */
    private boolean ignoreValidationErrors;

    /**
     * Whether to ignore response errors.  By default, an exception is
     * thrown if the Azure Search response contains an error.
     * When <code>true</code> the errors are logged instead.
     * @param ignoreResponseErrors <code>true</code> when ignoring response
     *        errors
     * @return <code>true</code> when ignoring response errors
     */
    private boolean ignoreResponseErrors;

    /**
     * The fields which values should always be treated as array.
     * Expects a comma-separated-value list or regular expression, based
     * on the returned value of {@link #isArrayFieldsRegex()}.
     * @param arrayFields list of fields or regular expression matching fields
     * @return list of fields or regular expression matching fields
     * @see #setArrayFieldsRegex(boolean)
     * @see #isArrayFieldsRegex()
     */
    private String arrayFields;

    /**
     * Whether the list of fields to be always treated as array
     * is represented as regular expression.
     * @param arrayFieldsRegex <code>true</code> if regular expression
     * @return <code>true</code> if regular expression
     * @see #setArrayFields(String)
     * @see #getArrayFields()
     */
    private boolean arrayFieldsRegex;

    /**
     * Whether to use integrated Windows Authentication (if applicable).
     * @param useWindowsAuth <code>true</code> if using Windows Authentication
     * @return <code>true</code> if using Windows Authentication
     */
    private boolean useWindowsAuth;

    private final ProxySettings proxySettings = new ProxySettings();

    /**
     * The document field name containing the value to be stored
     * in Azure Search document key field. Default is not a field
     * (<code>null</code>), but rather
     * the document reference.
     * @param sourceKeyField name of field containing id value,
     *        or <code>null</code>
     * @return name of field containing id value
     */
    private String sourceKeyField;

    /**
     * The name of Azure Search key field where to store a
     * document unique identifier (sourceKeyField).  Default is "id".
     * @param targetKeyField name of Solr ID field
     * @return name of Solr ID field
     */
    private String targetKeyField = DEFAULT_AZURE_KEY_FIELD;

    /**
     * The name of the Azure Search field where content will be stored.
     * Default is "content".
     * Specifying a <code>null</code> value will disable storing the content.
     * @param targetContentField field name
     * @return field name
     */
    private String targetContentField = DEFAULT_AZURE_CONTENT_FIELD;

    /**
     * Gets the proxy settings.
     * @return proxy settings (never <code>null</code>).
     */
    public ProxySettings getProxySettings() {
        return proxySettings;
    }

    /**
     * Sets the proxy settings.
     * @param proxySettings proxy settings
     */
    public AzureSearchCommitterConfig setProxySettings(
            @NonNull ProxySettings proxySettings
    ) {
        this.proxySettings.copyFrom(proxySettings);
        return this;
    }
}
