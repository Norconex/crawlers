/* Copyright 2021 Norconex Inc.
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

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Azure Search Committer configuration.
 * </p>
 * @author Pascal Essiembre
 */
public class AzureSearchCommitterConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default Azure Search API version */
    public static final String DEFAULT_API_VERSION = "2020-06-30";
    /** Default Azure Search document key field */
    public static final String DEFAULT_AZURE_KEY_FIELD = "id";
    /** Default Azure Search content field */
    public static final String DEFAULT_AZURE_CONTENT_FIELD = "content";

    // Configurables
    private String endpoint;
    private String apiVersion = DEFAULT_API_VERSION;
    private String apiKey;
    private String indexName;
    private boolean disableDocKeyEncoding;
    private boolean ignoreValidationErrors;
    private boolean ignoreResponseErrors;
    private String arrayFields;
    private boolean arrayFieldsRegex;
    private boolean useWindowsAuth;
    private final ProxySettings proxySettings = new ProxySettings();
    private String sourceKeyField;
    private String targetKeyField = DEFAULT_AZURE_KEY_FIELD;
    private String targetContentField = DEFAULT_AZURE_CONTENT_FIELD;

	/**
     * Gets the index name.
     * @return index name
     */
    public String getIndexName() {
        return indexName;
    }
    /**
     * Sets the index name.
     * @param indexName the index name
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    /**
     * Gets the Azure Search endpoint
     * (https://[service name].search.windows.net).
     * @return Azure Search endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }
    /**
     * Sets the Azure Search endpoint
     * (https://[service name].search.windows.net).
     * @param endpoint Azure Search endpoint
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Gets the Azure API version. Default is {@link #DEFAULT_API_VERSION}.
     * @return the Azure API version
     */
    public String getApiVersion() {
        return apiVersion;
    }
    /**
     * Sets the Azure API version.
     * @param apiVersion Azure API version
     */
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    /**
     * Gets the Azure API admin key.
     * @return Azure API admin key
     */
    public String getApiKey() {
        return apiKey;
    }
    /**
     * Sets the Azure API admin key.
     * @param apiKey Azure API admin key
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Whether to disable document reference encoding. By default, references
     * are encoded using a URL-safe Base64 encoding.  When <code>true</code>,
     * document references will be sent as is if they pass validation.
     * @return <code>true</code> if disabling reference encoding
     */
    public boolean isDisableDocKeyEncoding() {
        return disableDocKeyEncoding;
    }
    /**
     * Sets whether to disable document reference encoding. When
     * <code>false</code>, references are encoded using a URL-safe Base64
     * encoding.  When <code>true</code>, document references will be sent as
     * is if they pass validation.
     * @param disableReferenceEncoding <code>true</code> if disabling
     *        reference encoding
     */
    public void setDisableDocKeyEncoding(boolean disableReferenceEncoding) {
        this.disableDocKeyEncoding = disableReferenceEncoding;
    }

    /**
     * Whether to ignore validation errors.  By default, an exception is
     * thrown if a document contains a field that Azure Search will reject.
     * When <code>true</code> the validation errors are logged
     * instead and the faulty field or document is not committed.
     * @return <code>true</code> when ignoring validation errors
     */
    public boolean isIgnoreValidationErrors() {
        return ignoreValidationErrors;
    }
    /**
     * Sets whether to ignore validation errors.
     * When <code>false</code>, an exception is
     * thrown if a document contains a field that Azure Search will reject.
     * When <code>true</code> the validation errors are logged
     * instead and the faulty field or document is not committed.
     * @param ignoreValidationErrors <code>true</code> when ignoring validation
     *        errors
     */
    public void setIgnoreValidationErrors(boolean ignoreValidationErrors) {
        this.ignoreValidationErrors = ignoreValidationErrors;
    }

    /**
     * Whether to ignore response errors.  By default, an exception is
     * thrown if the Azure Search response contains an error.
     * When <code>true</code> the errors are logged instead.
     * @return <code>true</code> when ignoring response errors
     */
    public boolean isIgnoreResponseErrors() {
        return ignoreResponseErrors;
    }
    /**
     * Sets whether to ignore response errors.
     * When <code>false</code>, an exception is
     * thrown if the Azure Search response contains an error.
     * When <code>true</code> the errors are logged instead.
     * @param ignoreResponseErrors <code>true</code> when ignoring response
     *        errors
     */
    public void setIgnoreResponseErrors(boolean ignoreResponseErrors) {
        this.ignoreResponseErrors = ignoreResponseErrors;
    }

    /**
     * Gets the proxy settings.
     * @return proxy settings (never <code>null</code>).
     */
    public ProxySettings getProxySettings() {
        return proxySettings;
    }

    /**
     * Whether to use integrated Windows Authentication (if applicable).
     * @return <code>true</code> if using Windows Authentication
     */
    public boolean isUseWindowsAuth() {
        return useWindowsAuth;
    }
    /**
     * Sets whether to use integrated Windows Authentication (if applicable).
     * @param useWindowsAuth <code>true</code> if using Windows Authentication
     */
    public void setUseWindowsAuth(boolean useWindowsAuth) {
        this.useWindowsAuth = useWindowsAuth;
    }

    /**
     * Gets fields which values should always be treated as array.
     * Expects a comma-separated-value list or regular expression, based
     * on the returned value of {@link #isArrayFieldsRegex()}.
     * @return list of fields or regular expression matching fields
     * @see #isArrayFieldsRegex()
     */
    public String getArrayFields() {
        return arrayFields;
    }
    /**
     * Sets fields which values should always be treated as array.
     * Either a comma-separated-value list or regular expression, based
     * on the returned value of {@link #isArrayFieldsRegex()}.
     * @param arrayFields list of fields or regular expression matching fields
     * @see #setArrayFieldsRegex(boolean)
     */
    public void setArrayFields(String arrayFields) {
        this.arrayFields = arrayFields;
    }

    /**
     * Gets whether the list of fields to be always treated as array
     * is represented as regular expression.
     * @return <code>true</code> if regular expression
     * @see #getArrayFields()
     */
    public boolean isArrayFieldsRegex() {
        return arrayFieldsRegex;
    }
    /**
     * Sets whether the list of fields to be always treated as array
     * is represented as regular expression.
     * @param arrayFieldsRegex <code>true</code> if regular expression
     * @see #setArrayFields(String)
     */
    public void setArrayFieldsRegex(boolean arrayFieldsRegex) {
        this.arrayFieldsRegex = arrayFieldsRegex;
    }

    /**
     * Gets the document field name containing the value to be stored
     * in Azure Search document key field. Default is not a field, but rather
     * the document reference.
     * @return name of field containing id value
     */
    public String getSourceKeyField() {
        return sourceKeyField;
    }
    /**
     * Sets the document field name containing the value to be stored
     * in Azure Search key field. Set <code>null</code> to use the
     * document reference instead of a field (default).
     * @param sourceKeyField name of field containing id value,
     *        or <code>null</code>
     */
    public void setSourceKeyField(String sourceKeyField) {
        this.sourceKeyField = sourceKeyField;
    }
    /**
     * Gets the name of Azure Search key field where to store a
     * document unique identifier (sourceKeyField).  Default is "id".
     * @return name of Solr ID field
     */
    public String getTargetKeyField() {
        return targetKeyField;
    }
    /**
     * Sets the name of the Azure Search document key field where to store
     * a document unique identifier (sourceKeyField).
     * If not specified, default is "id".
     * @param targetKeyField name of Solr ID field
     */
    public void setTargetKeyField(String targetKeyField) {
        this.targetKeyField = targetKeyField;
    }
    /**
     * Gets the name of the Azure Search field where content will be stored.
     * Default is "content".
     * @return field name
     */
    public String getTargetContentField() {
        return targetContentField;
    }
    /**
     * Sets the name of the Azure Search field where content will be stored.
     * Specifying a <code>null</code> value will disable storing the content.
     * @param targetContentField field name
     */
    public void setTargetContentField(String targetContentField) {
        this.targetContentField = targetContentField;
    }

    void saveToXML(XML xml) {
        xml.addElement("endpoint", getEndpoint());
        xml.addElement("apiKey", getApiKey());
        xml.addElement("apiVersion", getApiVersion());
        xml.addElement("indexName", getIndexName());
        xml.addElement("useWindowsAuth", isUseWindowsAuth());
        xml.addElement("disableDocKeyEncoding", isDisableDocKeyEncoding());
        xml.addElement("ignoreValidationErrors", isIgnoreValidationErrors());
        xml.addElement("ignoreResponseErrors", isIgnoreResponseErrors());
        xml.addElement("arrayFields", getArrayFields())
                .setAttribute("regex", isArrayFieldsRegex());
        xml.addElement("sourceKeyField", getSourceKeyField());
        xml.addElement("targetKeyField", getTargetKeyField());
        xml.addElement("targetContentField", getTargetContentField());

        proxySettings.saveToXML(xml.addElement("proxySettings"));
    }
    void loadFromXML(XML xml) {
        setEndpoint(xml.getString("endpoint", getEndpoint()));
        setApiKey(xml.getString("apiKey", getApiKey()));
        setApiVersion(xml.getString("apiVersion", getApiVersion()));
        setIndexName(xml.getString("indexName", getIndexName()));
        setUseWindowsAuth(xml.getBoolean("useWindowsAuth", isUseWindowsAuth()));
        setDisableDocKeyEncoding(xml.getBoolean("disableDocKeyEncoding",
                isDisableDocKeyEncoding()));
        setIgnoreValidationErrors(xml.getBoolean("ignoreValidationErrors",
                isIgnoreValidationErrors()));
        setIgnoreResponseErrors(xml.getBoolean(
                "ignoreResponseErrors", isIgnoreResponseErrors()));
        setArrayFields(xml.getString("arrayFields", getArrayFields()));
        setArrayFieldsRegex(xml.getBoolean(
                "arrayFields/@regex", isArrayFieldsRegex()));
        setSourceKeyField(xml.getString("sourceKeyField", getSourceKeyField()));
        setTargetKeyField(xml.getString("targetKeyField", getTargetKeyField()));
        setTargetContentField(xml.getString(
                "targetContentField", getTargetContentField()));
        xml.ifXML("proxySettings", x -> x.populate(proxySettings));
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
