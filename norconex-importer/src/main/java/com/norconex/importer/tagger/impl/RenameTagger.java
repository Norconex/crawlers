/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Importer.
 * 
 * Norconex Importer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Importer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Importer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.importer.tagger.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.tagger.IDocumentTagger;

/**
 * <p>Rename metadata fields to different names.  If the target name already
 * exists, the values of the original field name will be added, unless
 * "overwrite" is set to <code>true</code>. 
 * </p>
 * <p>Can be used both as a pre-parse or post-parse handler.</p>
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;tagger class="com.norconex.importer.tagger.impl.RenameTagger"&gt;
 *      &lt;rename fromName="(from field)" toName="(to field)" overwrite="[false|true]" /&gt
 *      &lt;-- multiple rename tags allowed --&gt;
 *  &lt;/tagger&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
@SuppressWarnings("nls")
public class RenameTagger implements IDocumentTagger, IXMLConfigurable {

    private static final long serialVersionUID = 5747497256472060081L;

    private final Map<String, RenameDetails> renames = 
            new HashMap<String, RenameDetails>();
    
    @Override
    public void tagDocument(
            String reference, InputStream document, 
            Properties metadata, boolean parsed)
            throws IOException {
        for (String from : renames.keySet()) {
            RenameDetails details = renames.get(from);
            List<String> fromValues = metadata.get(from);
            List<String> toValues = metadata.get(details.toField);
            if (details.overwrite || toValues == null) {
                toValues = fromValues;
            } else if (fromValues != null) {
                fromValues.removeAll(toValues);
                toValues.addAll(fromValues);
            }
            metadata.put(details.toField, toValues);
            metadata.remove(from);
        }
    }

    public void addRename(String fromName, String toName, boolean overwrite) {
        if (StringUtils.isNotBlank(fromName) 
                && StringUtils.isNotBlank(toName)) {
            renames.put(fromName, 
                    new RenameDetails(fromName, toName, overwrite));
        }
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        try {
            XMLConfiguration xml = ConfigurationLoader.loadXML(in);
            List<HierarchicalConfiguration> nodes =
                    xml.configurationsAt("rename");
            for (HierarchicalConfiguration node : nodes) {
                addRename(node.getString("[@fromName]", null),
                          node.getString("[@toName]", null),
                          node.getBoolean("[@overwrite]", false));
            }
        } catch (ConfigurationException e) {
            throw new IOException("Cannot load XML.", e);
        }
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("tagger");
            writer.writeAttribute("class", getClass().getCanonicalName());
            
            for (String fromName : renames.keySet()) {
                RenameDetails details = renames.get(fromName);
                writer.writeStartElement("rename");
                writer.writeAttribute("fromName", details.fromField);
                writer.writeAttribute("toName", details.toField);
                writer.writeAttribute(
                        "overwrite", Boolean.toString(details.overwrite));
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
    
    public class RenameDetails {
        private String fromField;
        private String toField;
        private boolean overwrite;
        public RenameDetails(
                String fromField, String toField, boolean overwrite) {
            super();
            this.fromField = fromField;
            this.toField = toField;
            this.overwrite = overwrite;
        }
        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                .append(fromField)
                .append(toField)
                .append(overwrite)
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
            if (!(obj instanceof RenameTagger.RenameDetails)) {
                return false;
            }
            RenameTagger.RenameDetails other = (RenameTagger.RenameDetails) obj;
            return new EqualsBuilder()
                .append(fromField, other.fromField)
                .append(toField, other.toField)
                .append(overwrite, other.overwrite)
                .isEquals();
        }
        @Override
        public String toString() {
            return "RenameDetails [fromField=" + fromField + ", toField="
                    + toField + ", overwrite=" + overwrite + "]";
        }
    }
    
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(renames)
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
        if (!(obj instanceof RenameTagger)) {
            return false;
        }
        RenameTagger other = (RenameTagger) obj;
        return new EqualsBuilder()
            .append(renames, other.renames)
            .isEquals();
    }

    @Override
    public String toString() {
        return "RemapTagger [renames=" + renames + "]";
    }
}
