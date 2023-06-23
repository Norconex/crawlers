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

import java.util.Iterator;
import java.util.Objects;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringExclude;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Commits documents to Microsoft Azure Search.
 * </p>
 *
 * <h3>Document reference encoding</h3>
 * <p>
 * By default the document reference (Azure Search Document Key) is
 * encoded using URL-safe Base64 encoding. This is Azure Search recommended
 * approach when a document unique id can contain special characters
 * (e.g. a URL).  If you know your document references to be safe
 * (e.g. a sequence number), you can
 * set {@link AzureSearchCommitterConfig#setDisableDocKeyEncoding(boolean)}
 * to <code>true</code>.
 * To otherwise store a reference value un-encoded, you can additionally
 * store it in a field other than your reference ("id") field.
 * </p>
 *
 * <h3>Single vs multiple values</h3>
 * <p>
 * Fields with single value will be sent as such, while multi-value fields
 * are sent as array. If you have a field defined as an array in Azure Search,
 * sending a single value may cause an error.
 * </p>
 * <p>
 * It is possible for values to always
 * be sent as arrays for specific fields. This is done using
 * {@link AzureSearchCommitterConfig#setArrayFields(String)}.
 * It expects comma-separated-value list
 * or a regular expression, depending of the value you set for
 * {@link AzureSearchCommitterConfig#setArrayFieldsRegex(boolean)}.
 * </p>
 *
 * <h3>Field names and errors</h3>
 * <p>
 * Azure Search will produce an error if any of the documents in a submitted
 * batch contains one or more fields with invalid characters.  To prevent
 * sending those in vain, the committer will validate your fields
 * and throw an exception upon encountering an invalid one.
 * To prevent exceptions from being thrown, you can set
 * {@link AzureSearchCommitterConfig#setIgnoreValidationErrors(boolean)}
 * to <code>true</code> to log those errors instead.
 * </p>
 * <p>
 * An exception will also be thrown for errors returned by Azure Search
 * (e.g. a field is not defined in your
 * Azure Search schema). To also log those errors instead of throwing an
 * exception, you can set
 * {@link AzureSearchCommitterConfig#setIgnoreResponseErrors(boolean)}
 * to <code>true</code>.
 * </p>
 * <h4>Field naming rules</h4>
 * <p>
 * Those are the field naming rules mandated for Azure Search (in force
 * for Azure Search version 2016-09-01):
 * Search version
 * </p>
 * <ul>
 *   <li><b>Document reference (ID):</b> Letters, numbers, dashes ("-"),
 *       underscores ("_"), and equal signs ("="). First character cannot be
 *       an underscore.</li>
 *   <li><b>Document field name:</b> Letters, numbers, underscores ("_"). First
 *       character must be a letter. Cannot start with "azureSearch".
 *       Maximum length is 128 characters.</li>
 * </ul>
 *
 * {@nx.include com.norconex.commons.lang.security.Credentials#doc}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#restrictTo}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#fieldMappings}
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.azuresearch.AzureSearchCommitter">
 *   <endpoint>(Azure Search endpoint)</endpoint>
 *   <apiVersion>(Optional Azure Search API version to use)</apiVersion>
 *   <apiKey>(Azure Search API admin key)</apiKey>
 *   <indexName>(Name of the index to use)</indexName>
 *   <disableDocKeyEncoding>[false|true]</disableDocKeyEncoding>
 *   <ignoreValidationErrors>[false|true]</ignoreValidationErrors>
 *   <ignoreResponseErrors>[false|true]</ignoreResponseErrors>
 *   <useWindowsAuth>[false|true]</useWindowsAuth>
 *   <arrayFields regex="[false|true]">
 *     (Optional fields to be forcefully sent as array, even if single
 *     value. Unless "regex" is true, expects a CSV list of field names.)
 *   </arrayFields>
 *   <proxySettings>
 *     {@nx.include com.norconex.commons.lang.net.ProxySettings@nx.xml.usage}
 *   </proxySettings>
 *
 *   <sourceKeyField>
 *     (Optional document field name containing the value that will be stored
 *     in Azure Search target document key field. Default is the document
 *     reference.)
 *   </sourceKeyField>
 *   <targetKeyField>
 *     (Optional name of Azure Search document field where to store a
 *     document unique key identifier (sourceKeydField).
 *     Default is "id".)
 *   </targetKeyField>
 *   <targetContentField>
 *     (Optional Azure Search document field name to store document
 *     content/body. Default is "content".)
 *   </targetContentField>
 *
 *   {@nx.include com.norconex.committer.core.batch.AbstractBatchCommitter#options}
 * </committer>
 * }
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * {@nx.xml.example
 * <committer class="com.norconex.committer.azuresearch.AzureSearchCommitter">
 *   <endpoint>https://example.search.windows.net</endpoint>
 *   <apiKey>1234567890ABCDEF1234567890ABCDEF</apiKey>
 *   <indexName>sample-index</indexName>
 * </committer>
 * }
 * <p>
 * The above example uses the minimum required settings.
 * </p>
 *
 * @author Pascal Essiembre
 */
@SuppressWarnings("javadoc")
public class AzureSearchCommitter extends AbstractBatchCommitter {

    private final AzureSearchCommitterConfig config;

    @ToStringExclude
    @HashCodeExclude
    @EqualsExclude
    private AzureSearchClient client;

    public AzureSearchCommitter() {
        this(new AzureSearchCommitterConfig());
    }
    public AzureSearchCommitter(AzureSearchCommitterConfig config) {
        this.config = Objects.requireNonNull(
                config, "'config' must not be null.");
    }

    @Override
    protected void initBatchCommitter() throws CommitterException {
        client = new AzureSearchClient(config);
        if (getCommitterQueue() instanceof FSQueue
                && ((FSQueue) getCommitterQueue()).getBatchSize() > 1000) {
            throw new CommitterException(
                    "Commit batch size cannot be greater than 1000.");
        }
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {
        client.post(it);
    }

    @Override
    protected void closeBatchCommitter() throws CommitterException {
        if (client != null) {
            client.close();
        }
        client = null;
    }

    public AzureSearchCommitterConfig getConfig() {
        return config;
    }

    @Override
    protected void loadBatchCommitterFromXML(XML xml) {
        config.loadFromXML(xml);
    }
    @Override
    protected void saveBatchCommitterToXML(XML xml) {
        config.saveToXML(xml);
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
