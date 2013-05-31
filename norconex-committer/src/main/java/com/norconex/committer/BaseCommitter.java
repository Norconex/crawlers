/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Committer.
 * 
 * Norconex Committer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Committer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Committer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.committer;

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
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.io.FileUtil;
import com.norconex.commons.lang.io.IFileVisitor;
import com.norconex.commons.lang.map.Properties;

/**
 * Base implementation offering to batch the committing of documents.
 * 
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public abstract class BaseCommitter implements ICommitter, IXMLConfigurable {

    private static final long serialVersionUID = 880638478926236689L;
    private static final Logger LOG = LogManager.getLogger(BaseCommitter.class);

    public static final int DEFAULT_BATCH_SIZE = 1000;
    public static final String DEFAULT_SOLR_TARGET_ID = "id";
    public static final String DEFAULT_SOLR_TARGET_CONTENT = "content";
    public static final String DEFAULT_QUEUE_DIR = "./queue";

    private int batchSize = DEFAULT_BATCH_SIZE;
    private long docCount;

    protected final FileSystemCommitter queue = new FileSystemCommitter();

    protected String idTargetField;
    protected String idSourceField;
    protected boolean keepIdSourceField;
    protected String contentTargetField;
    protected String contentSourceField;
    protected boolean keepContentSourceField;

    private static final FileFilter NON_META_FILTER = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return !pathname.getName().endsWith(".meta");
        }
    };

    public BaseCommitter() {
        super();
    }

    public BaseCommitter(int batchSize) {
        super();
        this.batchSize = batchSize;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
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

    public final void queueAdd(String reference, File document,
            Properties metadata) {
        queue.queueAdd(reference, document, metadata);
        commitIfReady();
    }

    public final void queueRemove(String ref, File document, Properties metadata) {
        queue.queueRemove(ref, document, metadata);
        commitIfReady();
    }

    private void commitIfReady() {
        docCount++;
        if (docCount % batchSize == 0) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Batch size reached (" + batchSize + "). Committing");
            }
            commit();
        }
    }

    @Override
    public void commit() {

        // --- Additions ---
        FileUtil.visitAllFiles(queue.getAddDir(), new IFileVisitor() {
            @Override
            public void visit(File file) {
                try {
                    commitAdd(file, buildDocumentMap(file));
                } catch (IOException e) {
                    throw new CommitterException(
                            "Cannot create Solr Document for file: " + file, e);
                }
            }
        }, NON_META_FILTER);

        // --- Deletions ---
        FileUtil.visitAllFiles(queue.getRemoveDir(), new IFileVisitor() {
            @Override
            public void visit(File file) {
                try {
                    commitDelete(file, org.apache.commons.io.FileUtils
                            .readFileToString(file));
                } catch (IOException e) {
                    throw new CommitterException(
                            "Cannot read reference from : " + file, e);
                }
            }
        });

        commitComplete();
    }

    /**
     * Allow subclasses to commit files to be added.
     * 
     * The subclass has the responsibility of deleting the file once the content
     * is stored. The subclass may decide to batch those documents before
     * storing them.
     * 
     * @param file
     *            queued file
     * @param map
     *            map of data to store
     */
    protected abstract void commitAdd(File file, Map<String, String> map);

    /**
     * Allow subclasses to commit files to be deleted.
     * 
     * The subclass has the responsibility of deleting the file once the content
     * is stored. The subclass may decide to batch those deletions.
     * 
     * @param file
     *            queued file
     * @param id
     *            id of the content to be deleted
     */
    protected abstract void commitDelete(File file, String id);

    /**
     * Allow subclasses to operate upon the end of the commit operation.
     * 
     * For example, if the subclass decided to batch documents to commit, it may
     * decide to store all remaining documents on that event.
     * 
     */
    protected abstract void commitComplete();

    private Map<String, String> buildDocumentMap(File file) throws IOException {
        Properties metadata = loadMetadata(file);
        Map<String, String> map = new HashMap<String, String>();

        // --- Figure out field names ---
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

        // --- Add source to target field in document ---
        map.put(theIdTargetField, metadata.getString(theIdSourceField));
        String targetContent;
        if (StringUtils.isBlank(contentSourceField)) {
            targetContent = loadContentAsString(file);
        } else {
            targetContent = metadata.getString(theContentSourceField);
        }
        map.put(theContentTargetField, targetContent);

        // --- Remove non-kept source fields from metadata ---
        if (!keepIdSourceField && !theIdSourceField.equals(theIdTargetField)) {
            metadata.remove(theIdSourceField);
        }
        if (!keepContentSourceField
                && !ObjectUtils.equals(theContentSourceField,
                        theContentTargetField)
                && StringUtils.isNotBlank(theContentSourceField)) {
            metadata.remove(theContentSourceField);
        }

        // --- Add metadata entries to document ---
        for (String name : metadata.keySet()) {
            for (String value : metadata.get(name)) {
                map.put(name, value);
            }
        }

        return map;
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
            out.println(StringEscapeUtils.escapeXml(line.replaceAll("<.*?>",
                    " ")));
        }
        in.close();
        String content = sw.toString();
        out.close();
        return content;
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

            writer.writeStartElement("queueDir");
            writer.writeCharacters(getQueueDir());
            writer.writeEndElement();

            writer.writeStartElement("batchSize");
            writer.writeCharacters(ObjectUtils.toString(getBatchSize()));
            writer.writeEndElement();

            saveToXML(writer);

            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }

    /**
     * Allows subclasses to write their config to xml
     * 
     * @param writer
     */
    protected abstract void saveToXML(XMLStreamWriter writer)
            throws XMLStreamException;

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setIdSourceField(xml.getString("idSourceField", null));
        setKeepIdSourceField(xml.getBoolean("idSourceField[@keep]", false));
        setIdTargetField(xml.getString("idTargetField", null));
        setContentSourceField(xml.getString("contentSourceField", null));
        setKeepContentSourceField(xml.getBoolean("contentSourceField[@keep]",
                false));
        setContentTargetField(xml.getString("contentTargetField", null));
        setQueueDir(xml.getString("queueDir", DEFAULT_QUEUE_DIR));
        setBatchSize(xml.getInt("batchSize", BaseCommitter.DEFAULT_BATCH_SIZE));

        loadFromXml(xml);
    }

    /**
     * Allows subclasses to load their config from xml
     * 
     * @param xml
     */
    protected abstract void loadFromXml(XMLConfiguration xml);

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(contentSourceField)
                .append(keepContentSourceField).append(contentTargetField)
                .append(idSourceField).append(keepIdSourceField)
                .append(idTargetField).append(queue).toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof BaseCommitter)) {
            return false;
        }
        BaseCommitter other = (BaseCommitter) obj;
        return new EqualsBuilder()
                .append(contentSourceField, other.contentSourceField)
                .append(keepContentSourceField, other.keepContentSourceField)
                .append(contentTargetField, other.contentTargetField)
                .append(idSourceField, other.idSourceField)
                .append(keepIdSourceField, other.keepIdSourceField)
                .append(idTargetField, other.idTargetField)
                .append(queue, other.queue).isEquals();
    }

    @Override
    public String toString() {
        return String
                .format("BaseCommitter [batchSize=%s, docCount=%s, queue=%s, idTargetField=%s, idSourceField=%s, keepIdSourceField=%s, contentTargetField=%s, contentSourceField=%s, keepContentSourceField=%s]",
                        batchSize, docCount, queue, idTargetField,
                        idSourceField, keepIdSourceField, contentTargetField,
                        contentSourceField, keepContentSourceField);
    }
}
