/* Copyright 2016-2023 Norconex Inc.
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
package com.norconex.committer.amazoncloudsearch;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.norconex.committer.core.batch.BaseBatchCommitterConfig;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.time.DurationParser;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Commits documents to Amazon CloudSearch.
 * </p>
 * <h3>Authentication:</h3>
 * <p>
 * An access key and security key are required to connect to and interact with
 * CloudSearch. For enhanced security, it is best to use one of the methods
 * described in {@link DefaultAWSCredentialsProviderChain} for setting them
 * (environment variables, system properties, profile file, etc).
 * Do not explicitly set "accessKey" and "secretKey" on this class if you
 * want to rely on safer methods.
 * </p>
 * <h3>CloudSearch ID limitations:</h3>
 * <p>
 * As of this writing, CloudSearch has a 128 characters length limitation
 * on its "id" field. In addition, certain characters are not allowed.
 * By default, an error will result from trying to submit
 * documents with an invalid ID. You can get around this by
 * setting {@link #setFixBadIds(boolean)} to <code>true</code>.  It will
 * truncate references that are too long and append a hash code to it
 * to keep uniqueness.  It will also convert invalid
 * characters to underscore.  This approach is not 100%
 * collision-free (uniqueness), but it should safely cover the vast
 * majority of cases.
 * </p>
 *
 * {@nx.include com.norconex.commons.lang.security.Credentials#doc}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#restrictTo}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#fieldMappings}
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.cloudsearch.CloudSearchCommitter">
 *
 *   <!-- Mandatory: -->
 *   <serviceEndpoint>(CloudSearch service endpoint)</serviceEndpoint>
 *
 *   <!-- Mandatory if not configured elsewhere: -->
 *   <accessKey>
 *     (Optional CloudSearch access key. Will be taken from environment
 *      when blank.)
 *   </accessKey>
 *   <secretKey>
 *     (Optional CloudSearch secret key. Will be taken from environment
 *      when blank.)
 *   </secretKey>
 *
 *   <!-- Optional settings: -->
 *   <fixBadIds>
 *     [false|true](Forces references to fit into a CloudSearch id field.)
 *   </fixBadIds>
 *   <signingRegion>(CloudSearch signing region)</signingRegion>
 *   <proxySettings>
 *     {@nx.include com.norconex.commons.lang.net.ProxySettings@nx.xml.usage}
 *   </proxySettings>
 *
 *   <sourceIdField>
 *     (Optional document field name containing the value that will be stored
 *     in CloudSearch "id" field. Default is the document reference.)
 *   </sourceIdField>
 *   <targetContentField>
 *     (Optional CloudSearch field name to store the document
 *     content/body. Default is "content".)
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
 *
 * {@nx.xml.example
 * <committer class="com.norconex.committer.cloudsearch.CloudSearchCommitter">
 *   <serviceEndpoint>search-example-xyz.some-region.cloudsearch.amazonaws.com</serviceEndpoint>
 * </committer>
 * }
 *
 * <p>
 * The above example uses the minimum required settings (relying on environment
 * variables for AWS keys).
 * </p>
 *
 * @author Pascal Essiembre
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class AmazonCloudSearchCommitterConfig
        extends BaseBatchCommitterConfig {

    /** Default CloudSearch content field */
    public static final String DEFAULT_COULDSEARCH_CONTENT_FIELD = "content";

    /**
     * The AWS service endpoint.
     * @param serviceEndpoint AWS service endpoint
     * @return AWS service endpoint
     */
    private String serviceEndpoint;

    /**
     * The the AWS signing region.
     * @param signingRegion the AWS signing region
     * @return the AWS signing region
     */
    private String signingRegion;

    /**
     * The CloudSearch access key. If <code>null</code>, the access key
     * will be obtained from the environment, as detailed in
     * {@link DefaultAWSCredentialsProviderChain}.
     * @param accessKey the access key
     * @return the access key
     */
    private String accessKey;

    /**
     * The CloudSearch secret key. If <code>null</code>, the secret key
     * will be obtained from the environment, as detailed in
     * {@link DefaultAWSCredentialsProviderChain}.
     * @param secretKey the secret key
     * @return the secret key
     */
    private String secretKey;

    /**
     * Whether to fix IDs that are too long for CloudSearch
     * ID limitation (128 characters max). If <code>true</code>,
     * long IDs will be truncated and a hash code representing the
     * truncated part will be appended.
     * @param fixBadIds <code>true</code> to fix IDs that are too long
     * @return <code>true</code> to fix IDs that are too long
     */
    private boolean fixBadIds;

    private final ProxySettings proxySettings = new ProxySettings();

    /**
     * The document field name containing the value to be stored
     * in CloudSearch "id" field. Default is not a field, but rather
     * the document reference.
     * A <code>null</code> value indicate to use the
     * document reference instead of a field (default).
     * @param sourceIdField name of field containing id value,
     *        or <code>null</code>
     * @return name of field containing id value
     */
    private String sourceIdField;

    /**
     * The name of the CloudSearch field where content will be stored.
     * Default is "content".
     * Specifying a <code>null</code> value will disable storing the content.
     * @param targetContentField field name
     * @return field name
     */
    private String targetContentField = DEFAULT_COULDSEARCH_CONTENT_FIELD;

    public ProxySettings getProxySettings() {
        return proxySettings;
    }

    public AmazonCloudSearchCommitterConfig setProxySettings(
            ProxySettings proxy
    ) {
        proxySettings.copyFrom(proxy);
        return this;
    }
}
