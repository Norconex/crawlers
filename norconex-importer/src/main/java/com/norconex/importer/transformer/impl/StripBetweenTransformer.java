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
package com.norconex.importer.transformer.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.transformer.AbstractStringTransformer;

/**
 * <p>Strips any content found between a matching start and end strings.  The
 * matching strings are defined in pairs and multiple ones can be specified
 * at once.</p>
 * 
 * <p>This class can be used as a pre-parsing (text content-types only) 
 * or post-parsing handlers.</p>
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;transformer class="com.norconex.importer.transformer.impl.StripBetweenTransformer"
 *          inclusive="[false|true]" 
 *          caseSensitive="[false|true]" &gt;
 *      &lt;contentTypeRegex&gt;
 *          (regex to identify text content-types for pre-import, 
 *           overriding default)
 *      &lt;/contentTypeRegex&gt;
 *      &lt;restrictTo
 *              caseSensitive="[false|true]" &gt;
 *              property="(name of header/metadata name to match)"
 *          (regular expression of value to match)
 *      &lt;/restrictTo&gt;
 *      &lt;stripBetween&gt
 *          &lt;start&gt(regex)&lt;/start&gt
 *          &lt;end&gt(regex)&lt;/end&gt
 *      &lt;/stripBetween&gt
 *      &lt;-- multiple strignBetween tags allowed --&gt;
 *  &lt;/transformer&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class StripBetweenTransformer extends AbstractStringTransformer
        implements IXMLConfigurable {

    private static final long serialVersionUID = 9192256155691565491L;
    
    private Set<Pair<String, String>> stripPairs = 
            new TreeSet<Pair<String,String>>(
                    new Comparator<Pair<String,String>>() {
        @Override
        public int compare(Pair<String,String> o1, Pair<String,String> o2) {
            return o1.getLeft().length() - o2.getLeft().length();
        }
    });
    private boolean inclusive;
    private boolean caseSensitive;

    @Override
    protected void transformStringDocument(String reference,
            StringBuilder content, Properties metadata, boolean parsed,
            boolean partialContent) {
        int flags = Pattern.DOTALL | Pattern.UNICODE_CASE;
        if (!caseSensitive) {
            flags = flags | Pattern.CASE_INSENSITIVE;
        }
        for (Pair<String, String> pair : stripPairs) {
            List<Pair<Integer, Integer>> matches = 
                    new ArrayList<Pair<Integer, Integer>>();
            Pattern leftPattern = Pattern.compile(pair.getLeft(), flags);
            Matcher leftMatch = leftPattern.matcher(content);
            while (leftMatch.find()) {
                Pattern rightPattern = Pattern.compile(pair.getRight(), flags);
                Matcher rightMatch = rightPattern.matcher(content);
                if (rightMatch.find(leftMatch.end())) {
                    if (inclusive) {
                        matches.add(new ImmutablePair<Integer, Integer>(
                                leftMatch.start(), rightMatch.end()));
                    } else {
                        matches.add(new ImmutablePair<Integer, Integer>(
                                leftMatch.end(), rightMatch.start()));
                    }
                } else {
                    break;
                }
            }
            for (int i = matches.size() -1; i >= 0; i--) {
                Pair<Integer, Integer> matchPair = matches.get(i);
                content.delete(matchPair.getLeft(), matchPair.getRight());
            }
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
        setCaseSensitive(xml.getBoolean("[@caseSensitive]", false));
        setInclusive(xml.getBoolean("[@inclusive]", false));
        super.loadFromXML(xml);
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
            writer.writeAttribute(
                    "caseSensitive", Boolean.toString(isCaseSensitive()));
            writer.writeAttribute("inclusive", Boolean.toString(isInclusive()));
            super.saveToXML(writer);
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
        return new HashCodeBuilder()
            .append(caseSensitive)
            .append(inclusive)
            .append(stripPairs)
            .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        StripBetweenTransformer other = (StripBetweenTransformer) obj;
        if (caseSensitive != other.caseSensitive) {
            return false;
        }
        if (inclusive != other.inclusive) {
            return false;
        }
        if (stripPairs == null) {
            if (other.stripPairs != null) {
                return false;
            }
        } else if (!stripPairs.equals(other.stripPairs)) {
            return false;
        }
        return true;
    }

}
