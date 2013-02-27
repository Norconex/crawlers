package com.norconex.importer.tagger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;

import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.meta.Metadata;

/**
 * Define and add constant values to documents.  To add multiple constant values
 * under the same constant name, repeat the constant entry with a different 
 * value.
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;tagger class="com.norconex.importer.tagger.ConstantTagger"&gt;
 *      &lt;constant name="CONSTANT_NAME"&gtConstant Value&lt;/constant&gt
 *      &lt;-- multiple constant tags allowed --&gt;
 *  &lt;/tagger&gt;
 * </pre>
 * @author Pascal Essiembre
 */
@SuppressWarnings("nls")
public class ConstantTagger 
        implements IDocumentTagger, IXMLConfigurable {

    private static final long serialVersionUID = -6062036871216739761L;
    
    private final Map<String, List<String>> constants = 
            new HashMap<String, List<String>>();
    
    public void tagDocument(
            String reference, Reader document, Metadata metadata)
            throws IOException {
        for (String name : constants.keySet()) {
            List<String> values = constants.get(name);
            if (values != null) {
                for (String value : values) {
                    metadata.addPropertyValue(name, value);
                }
            }
        }
    }

    
    
    public Map<String, List<String>> getConstants() {
        return Collections.unmodifiableMap(constants);
    }

    public void addConstant(String name, String value) {
        if (name != null && value != null) {
            List<String> values = constants.get(name);
            if (values == null) {
                values = new ArrayList<String>(1);
                constants.put(name, values);
            }
            values.add(value);
        }
    }
    public void removeConstant(String name) {
        constants.remove(name);
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        try {
            XMLConfiguration xml = ConfigurationLoader.loadXML(in);
            List<HierarchicalConfiguration> nodes =
                    xml.configurationsAt("constant");
            for (HierarchicalConfiguration node : nodes) {
                String name = node.getString("[@name]");
                String value = node.getString("");
                addConstant(name, value);
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
            
            for (String name : constants.keySet()) {
                List<String> values = constants.get(name);
                for (String value : values) {
                    if (value != null) {
                        writer.writeStartElement("constant");
                        writer.writeAttribute("name", name);
                        writer.writeCharacters(value);
                        writer.writeEndElement();
                    }
                }
            }
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
        builder.append("ConstantTagger [{");
        boolean first = true;
        for (String name : constants.keySet()) {
            List<String> values = constants.get(name);
            for (String value : values) {
                if (value != null) {
                    if (!first) {
                        builder.append(", ");
                    }
                    builder.append("[name=").append(name)
                        .append(", value=").append(value)
                        .append("]");
                    first = false;
                }
            }
        }
        builder.append("}]");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((constants == null) ? 0 : constants.hashCode());
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
        ConstantTagger other = (ConstantTagger) obj;
        if (constants == null) {
            if (other.constants != null)
                return false;
        } else if (!constants.equals(other.constants))
            return false;
        return true;
    }
    
}
