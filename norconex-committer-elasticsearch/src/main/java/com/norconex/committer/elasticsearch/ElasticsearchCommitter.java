/* Copyright 2013 Norconex Inc.
 * 
 * This file is part of Norconex ElasticSearch Committer.
 * 
 * Norconex ElasticSearch Committer is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation, either version 3 of the License, 
 * or (at your option) any later version.
 * 
 * Norconex ElasticSearch Committer is distributed in the hope that it will be 
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex ElasticSearch Committer. 
 * If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.committer.elasticsearch;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;

import com.norconex.committer.BaseCommitter;
import com.norconex.committer.CommitterException;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;

/**
 * Commits documents to Elasticsearch.
 * <p>
 * XML configuration usage:
 * </p>
 * 
 * <pre>
 *  &lt;committer class="com.norconex.committer.elasticsearch.
 *      ElasticsearchCommitter"&gt;
 *      &lt;indexName&gt;(Name of the index to use)&lt;/indexName&gt;
 *      &lt;typeName&gt;(Name of the type to use)&lt;/typeName&gt;
 *      &lt;clusterName&gt;
 *         (Name of the ES cluster. Use if you have multiple clusters.)
 *      &lt;/clusterName&gt;
 *      &lt;idSourceField keep="[false|true]"&gt;
 *         (Name of source field that will be mapped to the ES "id" field.
 *         Default is the document reference metadata field: 
 *         "document.reference".  The metadata source field is 
 *         deleted, unless "keep" is set to <code>true</code>.)
 *      &lt;/idSourceField&gt;
 *      &lt;idTargetField&gt;(Unsupported)&lt;/idTargetField&gt;
 *      &lt;contentSourceField keep="[false|true]&gt;
 *         (If you wish to use a metadata field to act as the document 
 *         "content", you can specify that field here.  Default 
 *         does not take a metadata field but rather the document content.
 *         Once re-mapped, the metadata source field is deleted,
 *         unless "keep" is set to <code>true</code>.)
 *      &lt;/contentSourceField&gt;
 *      &lt;contentTargetField&gt;
 *         (ES target field name for a document content/body.
 *          Default is: content)
 *      &lt;/contentTargetField&gt;
 *      &lt;queueDir&gt;(optional path where to queue files)&lt;/queueDir&gt;
 *      &lt;batchSize&gt;(queue size before sending to ES)&lt;/batchSize&gt;
 *      &lt;elasticsearchBatchSize&gt;
 *          (max number of docs to send ES at once)
 *      &lt;/elasticsearchBatchSize&gt;
 *  &lt;/committer&gt;
 * </pre>
 * 
 * @author <a href="mailto:pascal.dimassimo@norconex.com">Pascal Dimassimo</a>
 * 
 */
public class ElasticsearchCommitter extends BaseCommitter implements
        IXMLConfigurable {

    private static final long serialVersionUID = 7000534391754478817L;

    private static final Logger LOG = LogManager
            .getLogger(ElasticsearchCommitter.class);

    public static final String DEFAULT_ES_CONTENT_FIELD = "content";
    public static final int DEFAULT_ES_BATCH_SIZE = 100;

    private int esBatchSize = DEFAULT_ES_BATCH_SIZE;

    private final List<QueuedAddedDocument> docsToAdd = 
            new ArrayList<QueuedAddedDocument>();
    private final List<QueuedDeletedDocument> docsToRemove = 
            new ArrayList<QueuedDeletedDocument>();

    private final IClientFactory clientFactory;

    private Client client;

    private String clusterName;

    private String indexName;

    private String typeName;

    private BulkRequestBuilder bulkRequest;

    public ElasticsearchCommitter() {
        this(null);
    }

    public ElasticsearchCommitter(IClientFactory factory) {
        if (factory == null) {
            this.clientFactory = new DefaultClientFactory();
        } else {
            this.clientFactory = factory;
        }
        setContentTargetField(DEFAULT_ES_CONTENT_FIELD);
    }

    public int getElasticsearchBatchSize() {
        return esBatchSize;
    }

    public void setElasticsearchBatchSize(int esBatchSize) {
        this.esBatchSize = esBatchSize;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    @Override
    protected void commitAddedDocument(QueuedAddedDocument document)
            throws IOException {
        docsToAdd.add(document);
        if (docsToAdd.size() % esBatchSize == 0) {
            persistToEs();
        }
    }

    private void persistToEs() {

        if (StringUtils.isBlank(getIndexName())) {
            throw new CommitterException("Index name is undefined.");
        }
        if (StringUtils.isBlank(getTypeName())) {
            throw new CommitterException("Type name is undefined.");
        }

        LOG.info("Sending " + docsToAdd.size()
                + " documents to Elasticsearch for update.");

        try {
            if (client == null) {
                client = clientFactory.createClient(this);
            }

            BulkRequestBuilder bulkRequest = client.prepareBulk();
            bulkAddedDocuments(bulkRequest);
            sendBulkToES(bulkRequest);
            deleteAddedQueue();

            LOG.info("Done sending documents to Elasticsearch for update.");

        } catch (IOException e) {
            throw new CommitterException(
                    "Cannot index document batch to Elasticsearch.", e);
        }
    }

    /**
     * Add all queued documents to add to a {@link BulkRequestBuilder}
     * 
     * @param bulkRequest
     * @throws IOException
     */
    private void bulkAddedDocuments(BulkRequestBuilder bulkRequest)
            throws IOException {
        for (QueuedAddedDocument doc : docsToAdd) {
            IndexRequestBuilder request = client.prepareIndex(getIndexName(),
                    getTypeName());
            request.setId(doc.getMetadata().getString(getIdSourceField()));
            request.setSource(buildSourceContent(doc.getMetadata()));
            bulkRequest.add(request);
        }
    }

    /**
     * Send {@link BulkRequestBuilder} to ES
     * 
     * @param bulkRequest
     */
    private void sendBulkToES(BulkRequestBuilder bulkRequest) {
        BulkResponse bulkResponse = bulkRequest.execute().actionGet();
        if (bulkResponse.hasFailures()) {
            throw new CommitterException(
                    "Cannot index document batch to Elasticsearch: "
                            + bulkResponse.buildFailureMessage());
        }
    }

    /**
     * Delete queued documents after commit
     */
    private void deleteAddedQueue() {
        for (QueuedAddedDocument doc : docsToAdd) {
            doc.deleteFromQueue();
        }
        docsToAdd.clear();
    }

    private XContentBuilder buildSourceContent(Properties fields)
            throws IOException {
        XContentBuilder builder = jsonBuilder().startObject();
        for (String key : fields.keySet()) {
            // Remove id from source unless specified to keep it
            if (!keepIdSourceField && key.equals(idSourceField)) {
                continue;
            }
            List<String> values = fields.getStrings(key);
            for (String value : values) {
                builder.field(key, value);
            }
        }
        return builder.endObject();
    }

    @Override
    protected void commitDeletedDocument(QueuedDeletedDocument document)
            throws IOException {
        docsToRemove.add(document);
        if (docsToRemove.size() % esBatchSize == 0) {
            deleteFromEs();
        }
    }

    private void deleteFromEs() {

        LOG.info("Sending " + docsToRemove.size()
                + " documents to Elasticsearch for deletion.");
        try {
            if (client == null) {
                client = clientFactory.createClient(this);
            }

            BulkRequestBuilder bulkRequest = client.prepareBulk();
            bulkDeletedDocuments(bulkRequest);
            sendBulkToES(bulkRequest);
            deleteDeletedQueue();

        } catch (Exception e) {
            throw new CommitterException(
                    "Cannot delete document batch from Elasticsearch.", e);
        }
        LOG.info("Done sending documents to Elasticsearch for deletion.");

    }

    private void deleteDeletedQueue() {
        // Delete queued documents after commit
        for (QueuedDeletedDocument doc : docsToRemove) {
            doc.deleteFromQueue();
        }
        docsToRemove.clear();
    }

    private void bulkDeletedDocuments(BulkRequestBuilder bulkRequest) {
        for (QueuedDeletedDocument doc : docsToRemove) {
            DeleteRequestBuilder request = client.prepareDelete(indexName,
                    typeName, doc.getReference());
            bulkRequest.add(request);
        }
    }

    @Override
    protected void commitComplete() {
        if (!docsToAdd.isEmpty()) {
            persistToEs();
        }
        if (!docsToRemove.isEmpty()) {
            deleteFromEs();
        }
    }

    @Override
    protected void saveToXML(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("elasticsearchBatchSize");
        writer.writeCharacters(ObjectUtils
                .toString(getElasticsearchBatchSize()));
        writer.writeEndElement();

        writer.writeStartElement("clusterName");
        writer.writeCharacters(clusterName);
        writer.writeEndElement();

        writer.writeStartElement("indexName");
        writer.writeCharacters(indexName);
        writer.writeEndElement();

        writer.writeStartElement("typeName");
        writer.writeCharacters(typeName);
        writer.writeEndElement();
    }

    @Override
    protected void loadFromXml(XMLConfiguration xml) {
        if (StringUtils.isNotBlank(xml.getString("idTargetField"))) {
            throw new UnsupportedOperationException(
                    "idTargetField is not supported by ElasticsearchCommitter");
        }

        setElasticsearchBatchSize(xml.getInt("elasticsearchBatchSize",
                DEFAULT_ES_BATCH_SIZE));
        setClusterName(xml.getString("clusterName", null));
        setIndexName(xml.getString("indexName", null));
        setTypeName(xml.getString("typeName", null));
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().appendSuper(super.hashCode())
                .append(esBatchSize).append(clusterName).append(indexName)
                .append(typeName).toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof ElasticsearchCommitter)) {
            return false;
        }
        ElasticsearchCommitter other = (ElasticsearchCommitter) obj;
        return new EqualsBuilder().appendSuper(super.equals(obj))
                .append(esBatchSize, other.esBatchSize)
                .append(clusterName, other.clusterName)
                .append(indexName, other.indexName)
                .append(typeName, other.typeName).isEquals();
    }

    @Override
    public String toString() {
        return String.format("ElasticsearchCommitter "
                + "[esBatchSize=%s, docsToAdd=%s, docsToRemove=%s, "
                + "clientFactory=%s, client=%s, clusterName=%s, "
                + "indexName=%s, typeName=%s, bulkRequest=%s, "
                + "BaseCommitter=%s]", esBatchSize, docsToAdd, docsToRemove,
                clientFactory, client, clusterName, indexName, typeName,
                bulkRequest, super.toString());
    }

}
