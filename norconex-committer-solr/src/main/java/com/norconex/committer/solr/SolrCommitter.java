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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.SolrInputDocument;

import com.norconex.committer.BatchableCommitter;
import com.norconex.committer.CommitterException;
import com.norconex.committer.FileSystemCommitter;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.io.FileUtil;
import com.norconex.commons.lang.io.IFileVisitor;
import com.norconex.commons.lang.map.Properties;

/**
 * Commits documents to Apache Solr.
 * <p>
 * XML configuration usage:
 * </p>
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
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class SolrCommitter extends BatchableCommitter 
        implements IXMLConfigurable {

    private static final long serialVersionUID = -842307672980791980L;

    private static final Logger LOG = 
            LogManager.getLogger(SolrCommitter.class);
    public static final String DEFAULT_QUEUE_DIR = "./queue";
    public static final int DEFAULT_SOLR_BATCH_SIZE = 100;
    public static final String DEFAULT_SOLR_TARGET_ID = "id";
    public static final String DEFAULT_SOLR_TARGET_CONTENT = "content";
    
    private String idTargetField;
    private String idSourceField;
    private boolean keepIdSourceField;
    private String contentTargetField;
    private String contentSourceField;
    private boolean keepContentSourceField;
    private String solrURL;
    private int solrBatchSize = DEFAULT_SOLR_BATCH_SIZE;
    private final FileSystemCommitter queue = new FileSystemCommitter();
    
    private static final FileFilter NON_META_FILTER = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return !pathname.getName().endsWith(".meta");
        }
    };
    
    public SolrCommitter() {
        super();
    }

    public String getQueueDir() {
        return queue.getDirectory();
    }
    public void setQueueDir(String queueDir) {
        this.queue.setDirectory(queueDir);
    }
    public String getIdSourceField() {
        return idSourceField;
    }
    public void setIdSourceField(String idSourceField) {
        this.idSourceField = idSourceField;
    }
    public String getIdTargetField() {
        return idTargetField;
    }
    public void setIdTargetField(String idTargetField) {
        this.idTargetField = idTargetField;
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
    public String getContentTargetField() {
        return contentTargetField;
    }
    public void setContentTargetField(String contentTargetField) {
        this.contentTargetField = contentTargetField;
    }
    public String getContentSourceField() {
        return contentSourceField;
    }
    public void setContentSourceField(String contentSourceField) {
        this.contentSourceField = contentSourceField;
    }
    public boolean isKeepIdSourceField() {
        return keepIdSourceField;
    }
    public void setKeepIdSourceField(boolean keepIdSourceField) {
        this.keepIdSourceField = keepIdSourceField;
    }
    public boolean isKeepContentSourceField() {
        return keepContentSourceField;
    }
    public void setKeepContentSourceField(boolean keepContentSourceField) {
        this.keepContentSourceField = keepContentSourceField;
    }

    @Override
    public void commit() {
        if (StringUtils.isBlank(solrURL)) {
            throw new CommitterException("Solr URL is undefined.");
        }
        final SolrServer server = new HttpSolrServer(solrURL);
        final MutableInt count = new MutableInt(0);
        
        //--- Additions ---
        final Map<File, SolrInputDocument> docsToAdd = 
                new HashMap<File, SolrInputDocument>();
        FileUtil.visitAllFiles(queue.getAddDir(), new IFileVisitor() {
            @Override
            public void visit(File file) {
                try {
                    addDocument(docsToAdd, file);
                } catch (IOException e) {
                    throw new CommitterException(
                            "Cannot create Solr Document for file: "
                                    + file, e);
                }
                count.increment();
                if (count.intValue() % solrBatchSize == 0) {
                    persistToSolr(server, docsToAdd);
                }
            }
        }, NON_META_FILTER);
        if (!docsToAdd.isEmpty()) {
            persistToSolr(server, docsToAdd);
        }
        
        //--- Deletions ---
        count.setValue(0);
        final Map<File, String> docsToRemove = 
                new HashMap<File, String>();
        FileUtil.visitAllFiles(queue.getRemoveDir(), new IFileVisitor() {
            @Override
            public void visit(File file) {
                try {
                    docsToRemove.put(file, org.apache.commons.io.FileUtils
                                    .readFileToString(file));
                } catch (IOException e) {
                    throw new CommitterException(
                            "Cannot read reference from : " + file, e);
                }
                count.increment();
                if (count.intValue() % solrBatchSize == 0) {
                    deleteFromSolr(server, docsToRemove);
                }
            }
        });
        if (!docsToRemove.isEmpty()) {
            deleteFromSolr(server, docsToRemove);
        }
    }

    private void persistToSolr(
            SolrServer server, Map<File, SolrInputDocument> docList) {
        LOG.info("Sending " + docList.size() 
                + " documents to Solr for update.");
        try {
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
    private void deleteFromSolr(
            SolrServer server, Map<File, String> docList) {
        LOG.info("Sending " + docList.size() 
                + " documents to Solr for deletion.");
        try {
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
    
    private void addDocument(Map<File, SolrInputDocument> docList, File file) 
            throws IOException {
        Properties metadata = loadMetadata(file);
        SolrInputDocument doc = new SolrInputDocument();

        //--- Figure out field names ---
        String theIdSourceField = getIdSourceField();
        if (StringUtils.isBlank(idSourceField)) {
            theIdSourceField = DEFAULT_DOCUMENT_REFERENCE;
        }

        String theIdTargetField = getIdTargetField();
        if (StringUtils.isBlank(theIdTargetField)) {
            theIdTargetField = DEFAULT_SOLR_TARGET_ID;
        }

        String theContentSourceField = getContentSourceField();
        
        String theContentTargetField = getContentTargetField();
        if (StringUtils.isBlank(theContentTargetField)) {
            theContentTargetField = DEFAULT_SOLR_TARGET_CONTENT;
        }

        //--- Add source to target field in document ---
        doc.addField(theIdTargetField, metadata.getString(theIdSourceField));
        String targetContent;
        if (StringUtils.isBlank(contentSourceField)) {
            targetContent = loadContentAsString(file);
        } else {
            targetContent = metadata.getString(theContentSourceField);
        }
        doc.addField(theContentTargetField, targetContent);

        
        //--- Remove non-kept source fields from metadata ---
        if (!keepIdSourceField
                && !theIdSourceField.equals(theIdTargetField)) {
            metadata.remove(theIdSourceField);
        }
        if (!keepContentSourceField && !ObjectUtils.equals(
                theContentSourceField, theContentTargetField)
                && StringUtils.isNotBlank(theContentSourceField)) {
            metadata.remove(theContentSourceField);
        }

        //--- Add metadata entries to document ---
        for (String name : metadata.keySet()) {
            for (String value : metadata.get(name)) {
                doc.addField(name, value);
            }
        }
        
        docList.put(file, doc);
    }

    @Override
    protected void queueBatchableAdd(
            String reference, File document, Properties metadata) {
        queue.queueAdd(reference, document, metadata);
    }

    @Override
    protected void queueBatchableRemove(
            String ref, File document, Properties metadata) {
        queue.queueRemove(ref, document, metadata);
    }

    private Properties loadMetadata(File file) throws IOException {
        Properties metadata = new Properties();
        File metaFile = new File(file.getAbsolutePath() + ".meta");
        if (metaFile.exists()) {
            FileInputStream is = new FileInputStream(metaFile);
            metadata.load(is);
            IOUtils.closeQuietly(is);
        }
        return metadata;
    }

    private String loadContentAsString(File file) throws IOException {
        FileReader reader = new FileReader(file);
        BufferedReader in = new BufferedReader(reader);
        String line = null;
        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        while ((line = in.readLine()) != null) {
            out.println(StringEscapeUtils.escapeXml(
                    line.replaceAll("<.*?>", " ")));
        }
        in.close();
        String content = sw.toString();
        out.close();
        return content;
    }
    
    
    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setIdSourceField(xml.getString("idSourceField", null));
        setKeepIdSourceField(xml.getBoolean("idSourceField[@keep]", false));
        setIdTargetField(xml.getString("idTargetField", null));
        setContentSourceField(xml.getString("contentSourceField", null));
        setKeepContentSourceField(xml.getBoolean(
                "contentSourceField[@keep]", false));
        setContentTargetField(xml.getString("contentTargetField", null));
        setSolrURL(xml.getString("solrURL", null));
        setQueueDir(xml.getString("queueDir", DEFAULT_QUEUE_DIR));
        setBatchSize(xml.getInt(
                "batchSize", BatchableCommitter.DEFAULT_BATCH_SIZE));
        setSolrBatchSize(xml.getInt("solrBatchSize", DEFAULT_SOLR_BATCH_SIZE));
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("committer");
            writer.writeAttribute("class", getClass().getCanonicalName());

            writer.writeStartElement("idSourceField");
            writer.writeAttribute("keep", Boolean.toString(keepIdSourceField));
            writer.writeCharacters(idSourceField);
            writer.writeEndElement();

            writer.writeStartElement("idTargetField");
            writer.writeCharacters(idTargetField);
            writer.writeEndElement();

            writer.writeStartElement("contentSourceField");
            writer.writeAttribute("keep", 
                    Boolean.toString(keepContentSourceField));
            writer.writeCharacters(contentSourceField);
            writer.writeEndElement();

            writer.writeStartElement("contentTargetField");
            writer.writeCharacters(contentTargetField);
            writer.writeEndElement();

            writer.writeStartElement("solrURL");
            writer.writeCharacters(solrURL);
            writer.writeEndElement();

            writer.writeStartElement("queueDir");
            writer.writeCharacters(getQueueDir());
            writer.writeEndElement();

            writer.writeStartElement("batchSize");
            writer.writeCharacters(ObjectUtils.toString(getBatchSize()));
            writer.writeEndElement();

            writer.writeStartElement("solrBatchSize");
            writer.writeCharacters(ObjectUtils.toString(getSolrBatchSize()));
            writer.writeEndElement();
            
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(contentSourceField)
            .append(keepContentSourceField)
            .append(contentTargetField)
            .append(idSourceField)
            .append(keepIdSourceField)
            .append(idTargetField)
            .append(queue)
            .append(solrBatchSize)
            .append(solrURL)
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
            .append(contentSourceField, other.contentSourceField)
            .append(keepContentSourceField, other.keepContentSourceField)
            .append(contentTargetField, other.contentTargetField)
            .append(idSourceField, other.idSourceField)
            .append(keepIdSourceField, other.keepIdSourceField)
            .append(idTargetField, other.idTargetField)
            .append(queue, other.queue)
            .append(solrBatchSize, other.solrBatchSize)
            .append(solrURL, other.solrURL)
            .isEquals();
    }
    
    @Override
    public String toString() {
        return "SolrCommitter [idTargetField=" + idTargetField
                + ", idSourceField=" + idSourceField + ", contentTargetField="
                + contentTargetField + ", contentSourceField="
                + contentSourceField + ", solrURL=" + solrURL
                + ", keepIdSourceField=" + keepIdSourceField
                + ", keepContentSourceField=" + keepContentSourceField
                + ", solrBatchSize=" + solrBatchSize + ", queue=" + queue + "]";
    }
}
