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
package com.norconex.importer.transformer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;

/**
 * <p>Strips any content found between a matching start and end strings.  The
 * matching strings are defined in pairs and multiple ones can be specified
 * at once.</p>
 * <p>
 * <p>This class can be used as a pre-parsing (text content-types only) 
 * or post-parsing handlers. See {@link AbstractTextTransformer} for more
 * details.</p>
 * </p>
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;transformer class="com.norconex.importer.transformer.StripBetweenTransformer"
 *          extraTextContentTypes="(comma-separated list of content types)"
 *          inclusive="[false|true]" 
 *          caseSensitive="[false|true]" &gt;
 *      &lt;stripBetween&gt
 *          &lt;start&gtStart Value&lt;/start&gt
 *          &lt;end&gtEnd Value&lt;/end&gt
 *      &lt;/stripBetween&gt
 *      &lt;-- multiple strignBetween tags allowed --&gt;
 *  &lt;/transformer&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class StripBetweenTransformer extends AbstractTextTransformer
        implements IXMLConfigurable {

    private static final long serialVersionUID = 9192256155691565491L;
    private static final Logger LOG = 
            LogManager.getLogger(StripBetweenTransformer.class);
    
    private Set<Pair<String, String>> stripPairs = 
            new TreeSet<Pair<String,String>>(
                    new Comparator<Pair<String,String>>() {
        public int compare(Pair<String,String> o1, Pair<String,String> o2) {
            return o1.getLeft().length() - o2.getLeft().length();
        };
    });
    private boolean inclusive;
    private boolean caseSensitive;

    @Override
    public void transformTextDocument(String reference, Reader input,
            Writer output, Properties metadata, boolean parsed)
            throws IOException {
        
        int maxMatchLength = getMaxMatchLength();
        long chunkSize = Math.min(maxMatchLength * 2, FileUtils.ONE_MB);
        long maxBufferSize = FileUtils.ONE_MB * 2;
        StringBuilder b = new StringBuilder();
        
        int i;
        PairMatch match = null;
        while ((i = input.read()) != -1) {
            char ch = (char) i;
            b.append(ch);
            
            // if we have buffered twice the max length of value to match:
            if (b.length() % chunkSize == 0) {
                if (match != null) { // we are stripping
                    int endIdx = getIndexForMatchingRight(b, match);
                    if (endIdx != -1) {
                        // Delete all before end tag
                        b.delete(0, endIdx);
                        match = null;
                    }
                } else {
                    match = getPairForMatchingLeft(b); 
                    if (match != null) {
                        // Write out buffer until start tag
                        output.write(b.substring(0, match.startIndex));
                        b.delete(0,  match.startIndex);
                    } else {
                        // Write out half the buffer
                        output.write(b.substring(0, maxMatchLength));
                        b.delete(0, maxMatchLength);
                    }
                }
            }
            
            // if buffer too big, abort the stripping.
            // TODO makes memory-configurable?  cache to disk if too big?
            // check how much memory from HEAP remains and deal with that?
            if (b.length() > maxBufferSize) {
                if (match != null) {
                    LOG.warn("Could not find closing text \""
                          + match.pair.getRight() + "\" for starting text \""
                          + match.pair.getLeft() + "\" after reading more than "
                          + chunkSize + " characters. Skipping this "
                          + "potential stripping match.");
                } else {
                    LOG.error("Internal buffer grew too big while there was "
                            + "no stripping match found.  This condition "
                            + "should never happen, please report.");
                }
                output.write(b.substring(0));
                b.setLength(0);
                match = null;
            }
            
        }
        output.write(b.toString());
        b.setLength(0);
        if (match != null) {
            LOG.info("Could not find closing text \""
                    + match.pair.getRight() + "\" for starting text \""
                    + match.pair.getLeft());
        }
    }
        
    public boolean isInclusive() {
        return inclusive;
    }
    /**
     * Sets whether start and end text pairs should themselves be stripped or 
     * not.
     * @param inclusive <code>true</code> to strip start and end text
     */
    public void setInclusive(boolean inclusive) {
        this.inclusive = inclusive;
    }
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    /**
     * Sets whether to ignore case when matching start and end text.
     * @param caseSensitive <code>true</code> to consider character case
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public void addStripEndpoints(String fromText, String toText) {
        if (StringUtils.isBlank(fromText) || StringUtils.isBlank(toText)) {
            return;
        }
        stripPairs.add(new ImmutablePair<String, String>(fromText, toText));
    }
    public List<Pair<String, String>> getStripEndpoints() {
        return new ArrayList<Pair<String,String>>(stripPairs);
    }
    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        super.loadFromXML(xml);
        setCaseSensitive(xml.getBoolean("[@caseSensitive]", false));
        setInclusive(xml.getBoolean("[@inclusive]", false));
        List<HierarchicalConfiguration> nodes = 
                xml.configurationsAt("stripBetween");
        for (HierarchicalConfiguration node : nodes) {
            addStripEndpoints(
                    node.getString("start", null), node.getString("end", null));
        }
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("transformer");
            writer.writeAttribute("class", getClass().getCanonicalName());
            super.saveToXML(writer);
            writer.writeAttribute(
                    "caseSensitive", Boolean.toString(isCaseSensitive()));
            writer.writeAttribute("inclusive", Boolean.toString(isInclusive()));
            for (Pair<String, String> pair : stripPairs) {
                writer.writeStartElement("stripBetween");
                writer.writeStartElement("start");
                writer.writeCharacters(pair.getLeft());
                writer.writeEndElement();
                writer.writeStartElement("end");
                writer.writeCharacters(pair.getRight());
                writer.writeEndElement();
                writer.writeEndElement();
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
        return "StripBetweenTransformer [stripPairs=" + stripPairs
                + ", inclusive=" + inclusive + ", caseSensitive="
                + caseSensitive + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (caseSensitive ? 1231 : 1237);
        result = prime * result + (inclusive ? 1231 : 1237);
        result = prime * result
                + ((stripPairs == null) ? 0 : stripPairs.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        StripBetweenTransformer other = (StripBetweenTransformer) obj;
        if (caseSensitive != other.caseSensitive)
            return false;
        if (inclusive != other.inclusive)
            return false;
        if (stripPairs == null) {
            if (other.stripPairs != null)
                return false;
        } else if (!stripPairs.equals(other.stripPairs))
            return false;
        return true;
    }
    
    private int getIndexForMatchingRight(StringBuilder b, PairMatch match) {
        int index = -1;
        String value = match.pair.getRight();
        if (caseSensitive) {
            index = b.indexOf(value);
        } else {
            index = StringUtils.indexOfIgnoreCase(b, value);
        }
        if (index != -1) {
            return inclusive ? index + value.length() : index;
        }
        return -1;        
    }
    
    private PairMatch getPairForMatchingLeft(StringBuilder b) {
        int index = -1;
        for (Pair<String, String> pair : stripPairs) {
            String value = pair.getLeft();
            if (caseSensitive) {
                index = b.indexOf(value);
            } else {
                index = StringUtils.indexOfIgnoreCase(b, value);
            }
            if (index != -1) {
                PairMatch match = new PairMatch();
                match.pair = pair;
                match.startIndex = inclusive ? index : index + value.length();
                return match;
            }
        }
        return null;
    }
    
    private int getMaxMatchLength() {
        int maxLength = 0;
        for (Pair<String, String> pair : getStripEndpoints()) {
            if (StringUtils.isNotBlank(pair.getLeft())) {
                maxLength = Math.max(maxLength, pair.getLeft().length());
            }
            if (StringUtils.isNotBlank(pair.getRight())) {
                maxLength = Math.max(maxLength, pair.getRight().length());
            }
        }
        return maxLength;
    }
    
    private class PairMatch {
        private Pair<String, String> pair;
        private int startIndex;
    }
}
