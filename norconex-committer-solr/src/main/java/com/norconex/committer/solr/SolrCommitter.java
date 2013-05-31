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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;

import com.norconex.committer.BaseCommitter;
import com.norconex.committer.CommitterException;

/**
 * Commits documents to Apache Solr.
 * <p>
 * XML configuration usage:
 * </p>
 * 
 * <pre>
 *  &lt;committer class="com.norconex.committer.solr.SolrCommitter"&gt;
 *      &lt;solrURL&gt;(URL to Solr)&lt;/solrURL&gt;
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
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class SolrCommitter extends BaseCommitter {

    private static final long serialVersionUID = -842307672980791980L;

    private static final Logger LOG = LogManager.getLogger(SolrCommitter.class);
    public static final int DEFAULT_SOLR_BATCH_SIZE = 100;

    private String solrURL;
    private int solrBatchSize = DEFAULT_SOLR_BATCH_SIZE;

    final private Map<File, SolrInputDocument> docsToAdd = new HashMap<File, SolrInputDocument>();
    final private Map<File, String> docsToRemove = new HashMap<File, String>();

    private SolrServer server;

    public SolrCommitter() {
        super();
    }

    /**
     * For unit tests
     * 
     * @param testServer
     */
    protected SolrCommitter(SolrServer testServer) {
        this.server = testServer;
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

    @Override
    protected void commitAdd(File file, Map<String, String> map) {
        docsToAdd.put(file, buildSolrDocument(map));
        if (docsToAdd.size() % solrBatchSize == 0) {
            persistToSolr(docsToAdd);
        }
    }

    private SolrInputDocument buildSolrDocument(Map<String, String> map) {
        SolrInputDocument doc = new SolrInputDocument();
        Iterator<Entry<String, String>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, String> entry = iterator.next();
            doc.addField(entry.getKey(), entry.getValue());
        }
        return doc;
    }

    private void initServer() {
        if (server == null) {
            if (StringUtils.isBlank(solrURL)) {
                throw new CommitterException("Solr URL is undefined.");
            }
            server = new HttpSolrServer(solrURL);
        }
    }

    private void persistToSolr(Map<File, SolrInputDocument> docList) {
        LOG.info("Sending " + docList.size() + " documents to Solr for update.");
        try {
            initServer();
            server.add(docList.values());
            server.commit();
            for (File file : docList.keySet()) {
                file.delete();
                new File(file.getAbsolutePath() + ".meta").delete();
            }
            docList.clear();
        } catch (Exception e) {
            throw new CommitterException(
                    "Cannot index document batch to Solr.", e);
        }
        LOG.info("Done sending documents to Solr for update.");
    }

    @Override
    protected void commitDelete(File file, String id) {
        docsToRemove.put(file, id);
        if (docsToRemove.size() % solrBatchSize == 0) {
            deleteFromSolr(docsToRemove);
        }
    }

    private void deleteFromSolr(Map<File, String> docList) {
        LOG.info("Sending " + docList.size()
                + " documents to Solr for deletion.");
        try {
            initServer();
            server.deleteById(new ArrayList<String>(docList.values()));
            server.commit();
            for (File file : docList.keySet()) {
                file.delete();
            }
            docList.clear();
        } catch (Exception e) {
            throw new CommitterException(
                    "Cannot delete document batch from Solr.", e);
        }
        LOG.info("Done sending documents to Solr for deletion.");
    }

    @Override
    protected void commitComplete() {
        if (!docsToAdd.isEmpty()) {
            persistToSolr(docsToAdd);
        }
        if (!docsToRemove.isEmpty()) {
            deleteFromSolr(docsToRemove);
        }
    }

    @Override
    protected void saveToXML(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("solrURL");
        writer.writeCharacters(solrURL);
        writer.writeEndElement();

        writer.writeStartElement("solrBatchSize");
        writer.writeCharacters(ObjectUtils.toString(getSolrBatchSize()));
        writer.writeEndElement();
    }

    @Override
    protected void loadFromXml(XMLConfiguration xml) {
        setSolrURL(xml.getString("solrURL", null));
        setSolrBatchSize(xml.getInt("solrBatchSize", DEFAULT_SOLR_BATCH_SIZE));
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().appendSuper(super.hashCode())
                .append(solrBatchSize).append(solrURL).toHashCode();
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
        return new EqualsBuilder().appendSuper(super.equals(obj))
                .append(solrBatchSize, other.solrBatchSize)
                .append(solrURL, other.solrURL).isEquals();
    }

    @Override
    public String toString() {
        return String
                .format("SolrCommitter [solrURL=%s, solrBatchSize=%s, docsToAdd=%s, docsToRemove=%s, server=%s, BaseCommitter=%s]",
                        solrURL, solrBatchSize, docsToAdd, docsToRemove,
                        server, super.toString());
    }

}
