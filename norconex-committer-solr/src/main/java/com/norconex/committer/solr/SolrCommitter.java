/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Committer Solr.
 * 
 * Norconex Committer Solr is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Committer Solr is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Committer Solr. If not, see 
 * <http://www.gnu.org/licenses/>.
 */
package com.norconex.committer.solr;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;

import com.norconex.committer.BaseCommitter;
import com.norconex.committer.CommitterException;
import com.norconex.commons.lang.map.Properties;

/**
 * Commits documents to Apache Solr.
 * <p>
 * XML configuration usage:
 * </p>
 * 
 * <pre>
 *  &lt;committer class="com.norconex.committer.solr.SolrCommitter"&gt;
 *      &lt;solrURL&gt;(URL to Solr)&lt;/solrURL&gt;
 *      &lt;solrUpdateURLParams&gt;
 *         &lt;param name="(parameter name)"&gt;(parameter value)&lt;/param&gt;
 *         &lt;-- multiple param tags allowed --&gt;
 *      &lt;/solrUpdateURLParams&gt;
 *      &lt;solrDeleteURLParams&gt;
 *         &lt;param name="(parameter name)"&gt;(parameter value)&lt;/param&gt;
 *         &lt;-- multiple param tags allowed --&gt;
 *      &lt;/solrDeleteURLParams&gt;
 *      &lt;idSourceField keep="[false|true]"&gt;
 *         (Name of source field that will be mapped to the Solr "id" field
 *         or whatever "idTargetField" specified.
 *         Default is the document reference metadata field: 
 *         "document.reference".  Once re-mapped, the metadata source field is 
 *         deleted, unless "keep" is set to <code>true</code>.)
 *      &lt;/idSourceField&gt;
 *      &lt;idTargetField&gt;
 *         (Name of Solr target field where the store a document unique 
 *         identifier (idSourceField).  If not specified, default is "id".) 
 *      &lt;/idTargetField&gt;
 *      &lt;contentSourceField keep="[false|true]&gt;
 *         (If you wish to use a metadata field to act as the document 
 *         "content", you can specify that field here.  Default 
 *         does not take a metadata field but rather the document content.
 *         Once re-mapped, the metadata source field is deleted,
 *         unless "keep" is set to <code>true</code>.)
 *      &lt;/contentSourceField&gt;
 *      &lt;contentTargetField&gt;
 *         (Solr target field name for a document content/body.
 *          Default is: content)
 *      &lt;/contentTargetField&gt;
 *      &lt;queueDir&gt;(optional path where to queue files)&lt;/queueDir&gt;
 *      &lt;batchSize&gt;(queue size before sending to Solr)&lt;/batchSize&gt;
 *      &lt;solrBatchSize&gt;
 *          (max number of docs to send Solr at once)
 *      &lt;/solrBatchSize&gt;
 *  &lt;/committer&gt;
 * </pre>
 * 
 * @author Pascal Essiembre
 */
//TODO test if same files can be picked up more than once when multi-threading
public class SolrCommitter extends BaseCommitter {

    private static final long serialVersionUID = -842307672980791980L;
    private static final Logger LOG = LogManager.getLogger(SolrCommitter.class);

    public static final int DEFAULT_SOLR_BATCH_SIZE = 100;
    public static final String DEFAULT_SOLR_ID_FIELD = "id";
    public static final String DEFAULT_SOLR_CONTENT_FIELD = "content";

    private String solrURL;
    private int solrBatchSize = DEFAULT_SOLR_BATCH_SIZE;

    private final List<QueuedAddedDocument> docsToAdd = 
            Collections.synchronizedList(new ArrayList<QueuedAddedDocument>());
    private final List<QueuedDeletedDocument> docsToRemove = 
            Collections.synchronizedList(
                    new ArrayList<QueuedDeletedDocument>());

    private final Map<String, String> updateUrlParams = 
            new HashMap<String, String>();
    private final Map<String, String> deleteUrlParams = 
            new HashMap<String, String>();
    
    private final ISolrServerFactory solrServerFactory;

    public SolrCommitter() {
        this(null);
    }
    public SolrCommitter(ISolrServerFactory solrServerFactory) {
        if (solrServerFactory == null) {
            this.solrServerFactory = new DefaultSolrServerFactory();
        } else {
            this.solrServerFactory = solrServerFactory;
        }
        setContentTargetField(DEFAULT_SOLR_CONTENT_FIELD);
        setIdTargetField(DEFAULT_SOLR_ID_FIELD);
    }

    public String getSolrURL() {
        return solrURL;
    }
    public void setSolrURL(String solrURL) {
        this.solrURL = solrURL;
    }
    public int getSolrBatchSize() {
        return solrBatchSize;
    }
    public void setSolrBatchSize(int solrBatchSize) {
        this.solrBatchSize = solrBatchSize;
    }

    public void setUpdateUrlParam(String name, String value) {
        updateUrlParams.put(name, value);
    }
    public void setDeleteUrlParam(String name, String value) {
        deleteUrlParams.put(name, value);
    }
    public String getUpdateUrlParam(String name) {
        return updateUrlParams.get(name);
    }
    public String getDeleteUrlParam(String name) {
        return deleteUrlParams.get(name);
    }
    public Set<String> getUpdateUrlParamNames() {
        return updateUrlParams.keySet();
    }
    public Set<String> getDeleteUrlParamNames() {
        return deleteUrlParams.keySet();
    }
    
    @Override
    protected void commitAddedDocument(QueuedAddedDocument document)
            throws IOException {
        docsToAdd.add(document);
        
        List<QueuedAddedDocument> batch = null;
        synchronized (docsToAdd) {
            if (docsToAdd.size() % solrBatchSize == 0) {
                batch = getBatchToAdd();
            }
        }
        if (batch != null) {
            persistToSolr(batch);
        }
    }

    @Override
    protected void commitDeletedDocument(QueuedDeletedDocument document)
            throws IOException {
        docsToRemove.add(document);
        
        List<QueuedDeletedDocument> batch = null;
        synchronized (docsToRemove) {
            if (docsToRemove.size() % solrBatchSize == 0) {
                batch = getBatchToRemove();
            }
        }
        if (batch != null) {
            deleteFromSolr(batch);
        }
    }

    private List<QueuedAddedDocument> getBatchToAdd() {
        List<QueuedAddedDocument> batch = 
                new ArrayList<QueuedAddedDocument>(docsToAdd);
        docsToAdd.clear();
        return batch;
    }

    private List<QueuedDeletedDocument> getBatchToRemove() {
        List<QueuedDeletedDocument> batch = 
                new ArrayList<QueuedDeletedDocument>(docsToRemove);
        docsToRemove.clear();
        return batch;
    }

    
    private void persistToSolr(List<QueuedAddedDocument> batch) {
        LOG.info("Sending " + batch.size() 
                + " documents to Solr for update.");
        try {
            // Commit Solr batch
            SolrServer server = solrServerFactory.createSolrServer(this);
            UpdateRequest request = new UpdateRequest();
            for (String name : updateUrlParams.keySet()) {
                request.setParam(name, updateUrlParams.get(name));
            }
            for (QueuedAddedDocument doc : batch) {
                request.add(buildSolrDocument(doc.getMetadata()));
            }
            request.process(server);
            server.commit();

            // Delete queued documents after commit
            for (QueuedAddedDocument doc : batch) {
                doc.deleteFromQueue();
            }
            batch.clear();
        } catch (Exception e) {
            throw new CommitterException(
                    "Cannot index document batch to Solr.", e);
        }
        LOG.info("Done sending documents to Solr for update.");
    }

    private SolrInputDocument buildSolrDocument(Properties fields) {
        SolrInputDocument doc = new SolrInputDocument();
        for (String key : fields.keySet()) {
            List<String> values = fields.getStrings(key);
            for (String value : values) {
                doc.addField(key, value);
            }
        }
        return doc;
    }
    
    private void deleteFromSolr(List<QueuedDeletedDocument> batch) {
        LOG.info("Sending " + batch.size()
                + " documents to Solr for deletion.");
        try {
            SolrServer server = solrServerFactory.createSolrServer(this);
            // Commit Solr batch
            UpdateRequest request = new UpdateRequest();
            for (String name : deleteUrlParams.keySet()) {
                request.setParam(name, deleteUrlParams.get(name));
            }
            for (QueuedDeletedDocument doc : batch) {
                request.deleteById(doc.getReference());
            }
            request.process(server);
            server.commit();

            // Delete queued documents after commit
            for (QueuedDeletedDocument doc : batch) {
                doc.deleteFromQueue();
            }
            batch.clear();
        } catch (Exception e) {
            throw new CommitterException(
                    "Cannot delete document batch from Solr.", e);
        }
        LOG.info("Done sending documents to Solr for deletion.");
    }

    @Override
    protected void commitComplete() {
        if (!docsToAdd.isEmpty()) {
            synchronized (docsToAdd) {
                persistToSolr(getBatchToAdd());
            }
        }
        if (!docsToRemove.isEmpty()) {
            synchronized (docsToRemove) {
                deleteFromSolr(getBatchToRemove());
            }
        }
    }

    @Override
    protected void saveToXML(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("solrURL");
        writer.writeCharacters(solrURL);
        writer.writeEndElement();

        writer.writeStartElement("solrUpdateURLParams");
        for (String name : updateUrlParams.keySet()) {
            writer.writeStartElement("param");
            writer.writeAttribute("name", name);
            writer.writeCharacters(updateUrlParams.get(name));
            writer.writeEndElement();
        }
        writer.writeEndElement();
        
        writer.writeStartElement("solrDeleteURLParams");
        for (String name : deleteUrlParams.keySet()) {
            writer.writeStartElement("param");
            writer.writeAttribute("name", name);
            writer.writeCharacters(deleteUrlParams.get(name));
            writer.writeEndElement();
        }
        writer.writeEndElement();
        
        writer.writeStartElement("solrBatchSize");
        writer.writeCharacters(ObjectUtils.toString(getSolrBatchSize()));
        writer.writeEndElement();
    }

    @Override
    protected void loadFromXml(XMLConfiguration xml) {
        setSolrURL(xml.getString("solrURL", null));
        setSolrBatchSize(xml.getInt("solrBatchSize", DEFAULT_SOLR_BATCH_SIZE));

        List<HierarchicalConfiguration> uparams = 
                xml.configurationsAt("solrUpdateURLParams.param");
        for (HierarchicalConfiguration param : uparams) {
            setUpdateUrlParam(param.getString("[@name]"), param.getString(""));
        }
        List<HierarchicalConfiguration> dparams = 
                xml.configurationsAt("solrDeleteURLParams.param");
        for (HierarchicalConfiguration param : dparams) {
            setDeleteUrlParam(param.getString("[@name]"), param.getString(""));
        }
    }

    
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(deleteUrlParams)
            .append(docsToAdd)
            .append(docsToRemove)
            .append(solrServerFactory)
            .append(solrURL)
            .append(updateUrlParams)
            .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof SolrCommitter)) {
            return false;
        }
        SolrCommitter other = (SolrCommitter) obj;
        return new EqualsBuilder()
            .appendSuper(super.equals(obj))
            .append(deleteUrlParams, other.deleteUrlParams)
            .append(docsToAdd, other.docsToAdd)
            .append(docsToRemove, other.docsToRemove)
            .append(solrBatchSize, other.solrBatchSize)
            .append(solrServerFactory, other.solrServerFactory)
            .append(solrURL, other.solrURL)
            .append(updateUrlParams, other.updateUrlParams)
            .isEquals();
    }
    
    @Override
    public String toString() {
        return "SolrCommitter [solrURL=" + solrURL + ", solrBatchSize="
                + solrBatchSize + ", docsToAdd=" + docsToAdd
                + ", docsToRemove=" + docsToRemove + ", updateUrlParams="
                + updateUrlParams + ", deleteUrlParams=" + deleteUrlParams
                + ", solrServerFactory=" + solrServerFactory
                + ", " + super.toString() + "]";
    }

    //TODO make it a top-level interface?  Make it XMLConfigurable?
    public interface ISolrServerFactory extends Serializable {
        SolrServer createSolrServer(SolrCommitter solrCommitter);
    }
    
    class DefaultSolrServerFactory implements ISolrServerFactory {
        private static final long serialVersionUID = 5820720860417411567L;
        private SolrServer server;
        @Override
        public SolrServer createSolrServer(SolrCommitter solrCommitter) {
            if (server == null) {
                if (StringUtils.isBlank(solrCommitter.getSolrURL())) {
                    throw new CommitterException("Solr URL is undefined.");
                }
                server = new HttpSolrServer(solrCommitter.getSolrURL());
            }
            return server;
        }
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + ((server == null) ? 0 : server.hashCode());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            DefaultSolrServerFactory other = (DefaultSolrServerFactory) obj;
            if (server == null) {
                if (other.server != null) {
                    return false;
                }
            } else if (!server.equals(other.server)) {
                return false;
            }
            return true;
        }
        @Override
        public String toString() {
            return "DefaultSolrServerFactory [server=" + server + "]";
        }
    }
}
