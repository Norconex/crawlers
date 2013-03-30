package com.norconex.importer.tagger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;

/**
 * <p>
 * Keep only the metadata fields provided, delete all other ones.
 * </p>
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;tagger class="com.norconex.importer.tagger.KeepOnlyTagger"
 *      fields="[coma-separated list of fields to keep]"/&gt
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
@SuppressWarnings("nls")
public class KeepOnlyTagger 
        implements IDocumentTagger, IXMLConfigurable {

    private static final long serialVersionUID = -4075527874358712815L;

    private final List<String> fields = new ArrayList<String>();
    
    public void tagDocument(
            String reference, Reader document, Properties metadata)
            throws IOException {
        for (String name : metadata.keySet()) {
            if (!exists(name)) {
                metadata.remove(name);
            }
        }
    }

    private boolean exists(String fieldToMatch) {
        for (String field : fields) {
            if (field.equalsIgnoreCase(fieldToMatch)) {
                return true;
            }
        }
        return false;
    }
    
    
    public List<String> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public void addField(String field) {
        fields.add(field);
    }
    public void removeField(String field) {
        fields.remove(field);
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        try {
            XMLConfiguration xml = ConfigurationLoader.loadXML(in);
            String fieldsStr = xml.getString("[@fields]");
            String[] fields = StringUtils.split(fieldsStr, ",");
            for (String field : fields) {
                addField(field.trim());
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
            writer.writeAttribute("fields", StringUtils.join(fields, ","));
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("KeepOnlyTagger [{");
        builder.append(StringUtils.join(fields, ","));
        builder.append("}]");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fields == null) ? 0 : fields.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        KeepOnlyTagger other = (KeepOnlyTagger) obj;
        if (fields == null) {
            if (other.fields != null)
                return false;
        } else if (!fields.equals(other.fields))
            return false;
        return true;
    }
}
