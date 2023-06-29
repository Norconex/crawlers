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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringExclude;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.cloudsearchdomain.AmazonCloudSearchDomain;
import com.amazonaws.services.cloudsearchdomain.AmazonCloudSearchDomainClientBuilder;
import com.amazonaws.services.cloudsearchdomain.model.UploadDocumentsRequest;
import com.amazonaws.services.cloudsearchdomain.model.UploadDocumentsResult;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterUtil;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.text.StringUtil;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;

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
public class AmazonCloudSearchCommitter extends AbstractBatchCommitter {

    private static final Logger LOG =
            LoggerFactory.getLogger(AmazonCloudSearchCommitter.class);

    /**
     * CouldSearch mandatory field pattern. Characters not matching
     * the pattern will be replaced by an underscore.
     */
    public static final Pattern FIELD_PATTERN = Pattern.compile(
            "[a-z0-9][a-z0-9_]{0,63}$");

    /** CloudSearch mandatory ID field */
    public static final String COULDSEARCH_ID_FIELD = "id";
    /** Default CloudSearch content field */
    public static final String DEFAULT_COULDSEARCH_CONTENT_FIELD = "content";

    @ToStringExclude
    @HashCodeExclude
    @EqualsExclude
    private AmazonCloudSearchDomain awsClient;

    private String serviceEndpoint;
    private String signingRegion;
    private String accessKey;
    private String secretKey;
    private boolean fixBadIds;
    private final ProxySettings proxySettings = new ProxySettings();
    private String sourceIdField;
    private String targetContentField = DEFAULT_COULDSEARCH_CONTENT_FIELD;

    public AmazonCloudSearchCommitter() {
        this(null);
    }
    public AmazonCloudSearchCommitter(String serviceEndpoint) {
        this(serviceEndpoint, null);
    }

    public AmazonCloudSearchCommitter(String serviceEndpoint, String signingRegion) {
        super();
        this.serviceEndpoint = serviceEndpoint;
        this.signingRegion = signingRegion;
        setTargetContentField(DEFAULT_COULDSEARCH_CONTENT_FIELD);
    }

    /**
     * Gets AWS service endpoint.
     * @return AWS service endpoint
     */
    public String getServiceEndpoint() {
        return serviceEndpoint;
    }
    /**
     * Sets AWS service endpoint.
     * @param serviceEndpoint AWS service endpoint
     */
    public void setServiceEndpoint(String serviceEndpoint) {
        this.serviceEndpoint = serviceEndpoint;
    }
    /**
     * Gets the AWS signing region.
     * @return the AWS signing region
     */
    public String getSigningRegion() {
        return signingRegion;
    }
    /**
     * Gets the AWS signing region.
     * @param signingRegion the AWS signing region
     */
    public void setSigningRegion(String signingRegion) {
        this.signingRegion = signingRegion;
    }

    /**
     * Gets the CloudSearch access key. If <code>null</code>, the access key
     * will be obtained from the environment, as detailed in
     * {@link DefaultAWSCredentialsProviderChain}.
     * @return the access key
     */
    public String getAccessKey() {
        return accessKey;
    }
    /**
     * Sets the CloudSearch access key.  If <code>null</code>, the access key
     * will be obtained from the environment, as detailed in
     * {@link DefaultAWSCredentialsProviderChain}.
     * @param accessKey the access key
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /**
     * Gets the CloudSearch secret key. If <code>null</code>, the secret key
     * will be obtained from the environment, as detailed in
     * {@link DefaultAWSCredentialsProviderChain}.
     * @return the secret key
     */
    public String getSecretKey() {
        return secretKey;
    }
    /**
     * Sets the CloudSearch secret key.  If <code>null</code>, the secret key
     * will be obtained from the environment, as detailed in
     * {@link DefaultAWSCredentialsProviderChain}.
     * @param secretKey the secret key
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Gets the name of the CloudSearch field where content will be stored.
     * Default is "content".
     * @return field name
     */
    public String getTargetContentField() {
        return targetContentField;
    }
    /**
     * Sets the name of the CloudSearch field where content will be stored.
     * Specifying a <code>null</code> value will disable storing the content.
     * @param targetContentField field name
     */
    public void setTargetContentField(String targetContentField) {
        this.targetContentField = targetContentField;
    }

    /**
     * Gets the document field name containing the value to be stored
     * in CloudSearch "id" field. Default is not a field, but rather
     * the document reference.
     * @return name of field containing id value
     */
    public String getSourceIdField() {
        return sourceIdField;
    }
    /**
     * Sets the document field name containing the value to be stored
     * in CloudSearch "id" field. Set <code>null</code> to use the
     * document reference instead of a field (default).
     * @param sourceIdField name of field containing id value,
     *        or <code>null</code>
     */
    public void setSourceIdField(String sourceIdField) {
        this.sourceIdField = sourceIdField;
    }

    /**
     * Gets whether to fix IDs that are too long for CloudSearch
     * ID limitation (128 characters max). If <code>true</code>,
     * long IDs will be truncated and a hash code representing the
     * truncated part will be appended.
     * @return <code>true</code> to fix IDs that are too long
     */
    public boolean isFixBadIds() {
        return fixBadIds;
    }
    /**
     * Sets whether to fix IDs that are too long for CloudSearch
     * ID limitation (128 characters max). If <code>true</code>,
     * long IDs will be truncated and a hash code representing the
     * truncated part will be appended.
     * @param fixBadIds <code>true</code> to fix IDs that are too long
     */
    public void setFixBadIds(boolean fixBadIds) {
        this.fixBadIds = fixBadIds;
    }

    public ProxySettings getProxySettings() {
        return proxySettings;
    }
    public void setProxySettings(ProxySettings proxy) {
        this.proxySettings.copyFrom(proxy);
    }

    @Override
    protected void initBatchCommitter() throws CommitterException {
        // Build AWS Client

        if (StringUtils.isBlank(getServiceEndpoint())) {
            throw new CommitterException("Service endpoint is undefined.");
        }
        AmazonCloudSearchDomainClientBuilder b =
                AmazonCloudSearchDomainClientBuilder.standard();
        ClientConfiguration clientConfig= new ClientConfiguration();
        if (proxySettings.isSet()) {
            clientConfig.setProxyHost(proxySettings.getHost().getName());
            clientConfig.setProxyPort(proxySettings.getHost().getPort());
            if (proxySettings.getCredentials().isSet()) {
                clientConfig.setProxyUsername(
                        proxySettings.getCredentials().getUsername());
                clientConfig.setProxyPassword(EncryptionUtil.decrypt(
                            proxySettings.getCredentials().getPassword(),
                            proxySettings.getCredentials().getPasswordKey()));
            }
        }
        b.setClientConfiguration(clientConfig);
        if (StringUtils.isAnyBlank(accessKey, secretKey)) {
            b.withCredentials(new DefaultAWSCredentialsProviderChain());
        } else {
            b.withCredentials(new AWSStaticCredentialsProvider(
                    new BasicAWSCredentials(accessKey, secretKey)));
        }
        b.withEndpointConfiguration(
                new EndpointConfiguration(serviceEndpoint, signingRegion));
        awsClient = b.build();
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {

        List<JSONObject> jsonBatch = new ArrayList<>();
        try {
            while (it.hasNext()) {
                CommitterRequest req = it.next();
                if (req instanceof UpsertRequest upsert) {
                    jsonBatch.add(toJsonDocUpsert(upsert));
                } else if (req instanceof DeleteRequest delete) {
                    jsonBatch.add(toJsonDocDelete(delete));
                } else {
                    throw new CommitterException("Unsupported request:" + req);
                }
            }
            uploadBatchToCloudSearch(jsonBatch);
        } catch (CommitterException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitterException(
                    "Could not commit JSON batch to CloudSearch.", e);
        }
    }

    @Override
    protected void closeBatchCommitter() throws CommitterException {
        if (awsClient != null) {
            awsClient.shutdown();
        }
        awsClient = null;
        LOG.info("Amazon Cloud Search client shut down.");
    }

    private void uploadBatchToCloudSearch(List<JSONObject> documentBatch)
            throws CommitterException {
        // Convert the JSON list to String and read it as a stream from memory
        // (for increased performance), for it to be usable by the AWS
        // CloudSearch UploadRequest. If memory becomes a concern, consider
        // streaming to file.
        // ArrayList.toString() joins the elements in a JSON-compliant way.
        byte[] bytes =
                documentBatch.toString().getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream is = new ByteArrayInputStream(bytes)) {
            UploadDocumentsRequest uploadRequest = new UploadDocumentsRequest();
            uploadRequest.setContentType("application/json");
            uploadRequest.setDocuments(is);
            uploadRequest.setContentLength((long) bytes.length);
            UploadDocumentsResult result =
                    awsClient.uploadDocuments(uploadRequest);
            LOG.info("{} upserts and {} deletes sent to the AWS CloudSearch "
                    + "domain.", result.getAdds(), result.getDeletes());
        } catch (IOException | AmazonServiceException e) {
            throw new CommitterException(
                    "Could not execute CloudSearch upload request.", e);
        }
    }

    private JSONObject toJsonDocUpsert(UpsertRequest req)
            throws CommitterException {

        CommitterUtil.applyTargetContent(req, targetContentField);

        Map<String, Object> documentMap = new HashMap<>();
        documentMap.put("type", "add");
        documentMap.put(COULDSEARCH_ID_FIELD, extractId(req));
        Map<String, Object> fieldMap = new HashMap<>();
        for (Entry<String, List<String>> en : req.getMetadata().entrySet()) {
            String key = en.getKey();
            List<String> values = en.getValue();
            if (!COULDSEARCH_ID_FIELD.equals(key)) {
                /*size = 1 : non-empty single-valued field
                  size > 1 : non-empty multi-valued field
                  size = 0 : empty field
                */
                String fixedKey = fixKey(key);
                if (values.size() == 1) {
                    fieldMap.put(fixedKey, values.get(0));
                } else if (values.size() > 1){
                    fieldMap.put(fixedKey, values);
                } else {
                    fieldMap.put(fixedKey, "");
                }
            }
        }
        documentMap.put("fields", fieldMap);
        return new JSONObject(documentMap);
    }

    private JSONObject toJsonDocDelete(DeleteRequest req)
            throws CommitterException {
        Map<String, Object> documentMap = new HashMap<>();
        documentMap.put("type", "delete");
        documentMap.put(COULDSEARCH_ID_FIELD, extractId(req));
        return new JSONObject(documentMap);
    }

    private String extractId(CommitterRequest req) throws CommitterException {
        return fixBadIdValue(
                CommitterUtil.extractSourceIdValue(req, sourceIdField));
    }

    private String fixBadIdValue(String value) throws CommitterException {
        if (StringUtils.isBlank(value)) {
            throw new CommitterException("Document id cannot be empty.");
        }

        if (fixBadIds) {
            String v = value.replaceAll(
                    "[^a-zA-Z0-9\\-\\_\\/\\#\\:\\.\\;\\&\\=\\?"
                  + "\\@\\$\\+\\!\\*'\\(\\)\\,\\%]", "_");
            v = StringUtil.truncateWithHash(v, 128, "!");
            if (LOG.isDebugEnabled() && !value.equals(v)) {
                LOG.debug("Fixed document id from \"{}\" to \"{}\".", value, v);
            }
            return v;
        }
        return value;
    }
    
    private String fixKey(String key) {
        if (FIELD_PATTERN.matcher(key).matches()) {
            return key;
        }
        String fix = key;
        fix = fix.replaceFirst("^[^a-zA-Z0-9]", "");
        fix = StringUtils.truncate(fix, 63);
        fix = fix.replaceAll("[^a-zA-Z0-9_]", "_");
        fix = fix.toLowerCase(Locale.ENGLISH);
        LOG.warn("\"{}\" field renamed to \"{}\" as it does not match "
                + "CloudSearch required pattern: {}", key, fix, FIELD_PATTERN);
        return fix;
    }

    @Override
    protected void saveBatchCommitterToXML(XML xml) {
        xml.addElement("serviceEndpoint", getServiceEndpoint());
        xml.addElement("signingRegion", getSigningRegion());
        xml.addElement("accessKey", getAccessKey());
        xml.addElement("secretKey", getSecretKey());
        xml.addElement("fixBadIds", isFixBadIds());
        xml.addElement("sourceIdField", getSourceIdField());
        xml.addElement("targetContentField", getTargetContentField());
        proxySettings.saveToXML(xml.addElement("proxySettings"));
    }

    @Override
    protected void loadBatchCommitterFromXML(XML xml) {
        setServiceEndpoint(xml.getString(
                "serviceEndpoint", getServiceEndpoint()));
        setSigningRegion(xml.getString("signingRegion", getSigningRegion()));
        setAccessKey(xml.getString("accessKey", getAccessKey()));
        setSecretKey(xml.getString("secretKey", getSecretKey()));
        setFixBadIds(xml.getBoolean("fixBadIds", isFixBadIds()));
        setSourceIdField(xml.getString("sourceIdField", getSourceIdField()));
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
