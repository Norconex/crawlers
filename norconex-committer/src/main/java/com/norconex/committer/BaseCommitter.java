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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;

/**
 * <p>A base class batching documents and offering mappings of source id and 
 * content fields to target id and content fields.  
 * Batched documents are queued on the file system.</p>
 * 
 * <h4>ID Mapping:</h4>
 * 
 * <p>Both the <code>idSourceField</code> and <code>idTargetField</code> must 
 * be set for ID mapping to take place. The default <b>source id</b> field is 
 * the metadata normally set by the Norconex Importer module called 
 * <code>document.reference</code>.  The default (or constant) <b>target id</b> 
 * field is for subclasses to define.  When an ID mapping is defined, the 
 * source id field will be deleted unless the <code>keepIdSourceField</code>
 * attribute is set to <code>true</code>.</p> 
 * 
 * <h4>Content Mapping:</h4>
 * 
 * <p>Only the <code>contentTargetField</code> needs to be set for content
 * mapping to take place.   The default <b>source content</b> is
 * the actual document content.  Defining a <code>contentSourceField</code>
 * will use the matching metadata property instead.
 * The default (or constant) <b>target content</b> field is for subclasses
 * to define.  When a content mapping is defined, the 
 * content id field will be deleted (if provided) unless the 
 * <code>keepContentSourceField</code> attribute is set to 
 * <code>true</code>.</p> 
 * 
 * @author Pascal Essiembre
 * @author Pascal Dimassimo
 */
@SuppressWarnings("nls")
public abstract class BaseCommitter
        extends FileSystemQueueCommitter implements IXMLConfigurable {

    private static final long serialVersionUID = 5437833425204155264L;

    private long docCount;

    protected String idTargetField;
    protected String idSourceField = DEFAULT_DOCUMENT_REFERENCE;
    protected boolean keepIdSourceField;
    protected String contentTargetField;
    protected String contentSourceField;
    protected boolean keepContentSourceField;

    public BaseCommitter() {
        super();
    }
    public BaseCommitter(int batchSize) {
        super(batchSize);
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

    @Override
    protected void preCommitAddedDocument(QueuedAddedDocument document)
            throws IOException {
        Properties metadata = document.getMetadata();

        //--- source ID -> target ID ---
        if (StringUtils.isNotBlank(idSourceField)
                && StringUtils.isNotBlank(idTargetField)) {
            metadata.setString(idTargetField, 
                    metadata.getString(idSourceField));
            if (!keepIdSourceField 
                    && !ObjectUtils.equals(idSourceField, idTargetField)) {
                metadata.remove(idSourceField);
            }
        }
        
        //--- source content -> target content ---
        if (StringUtils.isNotBlank(contentTargetField)) {
            if (StringUtils.isNotBlank(contentSourceField)) {
                List<String >content = metadata.getStrings(contentSourceField);
                metadata.setString(contentTargetField, 
                        content.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
                if (!keepContentSourceField && !ObjectUtils.equals(
                        contentSourceField, contentTargetField)) {
                    metadata.remove(contentSourceField);
                }
            } else {
                InputStream is = document.getContentStream();
                metadata.setString(contentTargetField, IOUtils.toString(is));
                IOUtils.closeQuietly(is);
            }
        }
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("committer");
            writer.writeAttribute("class", getClass().getCanonicalName());

            if (idSourceField != null) {
                writer.writeStartElement("idSourceField");
                writer.writeAttribute(
                        "keep", Boolean.toString(keepIdSourceField));
                writer.writeCharacters(idSourceField);
                writer.writeEndElement();
            }
            if (idTargetField != null) {
                writer.writeStartElement("idTargetField");
                writer.writeCharacters(idTargetField);
                writer.writeEndElement();
            }
            if (contentSourceField != null) {
                writer.writeStartElement("contentSourceField");
                writer.writeAttribute("keep",
                        Boolean.toString(keepContentSourceField));
                writer.writeCharacters(contentSourceField);
                writer.writeEndElement();
            }
            if (contentTargetField != null) {
                writer.writeStartElement("contentTargetField");
                writer.writeCharacters(contentTargetField);
                writer.writeEndElement();
            }
            if (getQueueDir() != null) {
                writer.writeStartElement("queueDir");
                writer.writeCharacters(getQueueDir());
                writer.writeEndElement();
            }
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
        setIdSourceField(xml.getString("idSourceField", idSourceField));
        setKeepIdSourceField(xml.getBoolean("idSourceField[@keep]", 
                keepIdSourceField));
        setIdTargetField(xml.getString("idTargetField", idTargetField));
        setContentSourceField(
                xml.getString("contentSourceField", contentSourceField));
        setKeepContentSourceField(xml.getBoolean("contentSourceField[@keep]", 
                keepContentSourceField));
        setContentTargetField(
                xml.getString("contentTargetField", contentTargetField));
        setQueueDir(xml.getString("queueDir", DEFAULT_QUEUE_DIR));
        setBatchSize(xml.getInt("batchSize", 
                BatchableCommitter.DEFAULT_BATCH_SIZE));

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
                .append(idTargetField).append(getQueueDir())
                .append(getBatchSize()).toHashCode();
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
                .append(getBatchSize(), other.getBatchSize())
                .append(getQueueDir(), other.getQueueDir()).isEquals();
    }

    @Override
    public String toString() {
        return String.format("BaseCommitter [batchSize=%s, docCount=%s, "
                + "queue=%s, idTargetField=%s, idSourceField=%s, "
                + "keepIdSourceField=%s, contentTargetField=%s, "
                + "contentSourceField=%s, keepContentSourceField=%s]",
                        getBatchSize(), docCount, getQueueDir(), idTargetField,
                        idSourceField, keepIdSourceField, contentTargetField,
                        contentSourceField, keepContentSourceField);
    }
}
