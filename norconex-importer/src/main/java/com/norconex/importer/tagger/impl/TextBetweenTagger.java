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
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
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
import org.apache.commons.lang.builder.CompareToBuilder;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.tagger.AbstractStringTagger;

/**
 * <p>Extracts and add values found between a matching start and 
 * end strings to a document metadata field.      
 * The matching string end-points are defined in pairs and multiple ones 
 * can be specified at once. The field specified for a pair of end-points
 * is considered a multi-value field.</p>
 * 
 * <p>This class can be used as a pre-parsing (text content-types only) 
 * or post-parsing handlers.</p>
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;tagger class="com.norconex.importer.transformer.impl.TextBetweenTagger"
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
 *      &lt;textBetween name="targetFieldName"&gt
 *          &lt;start&gt(regex)&lt;/start&gt
 *          &lt;end&gt(regex)&lt;/end&gt
 *      &lt;/textBetween&gt
 *      &lt;-- multiple textBetween tags allowed --&gt;
 *  &lt;/transformer&gt;
 * </pre>
 * @author Khalid AlHomoud
 * @author Pascal Essiembre
 */
public class TextBetweenTagger 
        extends AbstractStringTagger implements IXMLConfigurable {

    private static final long serialVersionUID = 7202856816087407447L;

    private Set<TextBetween> betweens = new TreeSet<TextBetween>();

    private boolean inclusive;
    private boolean caseSensitive;

    @Override
    protected void tagStringDocument(String reference, StringBuilder content,
            Properties metadata, boolean parsed, boolean partialContent) {
        int flags = Pattern.DOTALL | Pattern.UNICODE_CASE;
        if (!caseSensitive) {
            flags = flags | Pattern.CASE_INSENSITIVE;
        }
        for (TextBetween between : betweens) {
            List<Pair<Integer, Integer>> matches = 
                    new ArrayList<Pair<Integer, Integer>>();
            Pattern leftPattern = Pattern.compile(between.start, flags);
            Matcher leftMatch = leftPattern.matcher(content);
            while (leftMatch.find()) {
                Pattern rightPattern = Pattern.compile(between.end, flags);
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
                String value = content.substring(
                        matchPair.getLeft(), matchPair.getRight());
                if (value != null) {
                    metadata.addString(between.name, value);
                }
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
    /**
     * Adds a new pair of end points to match.
     * @param name target metadata field name where to store the extracted
     *             values
     * @param fromText
     * @param toText
     */
    public void addTextEndpoints(String name, String fromText, String toText) {
        if (StringUtils.isBlank(name)
                || StringUtils.isBlank(fromText)
                || StringUtils.isBlank(toText)) {
            return;
        }
        betweens.add(new TextBetween(name, fromText, toText));
    }
    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setCaseSensitive(xml.getBoolean("[@caseSensitive]", false));
        setInclusive(xml.getBoolean("[@inclusive]", false));
        super.loadFromXML(xml);
        List<HierarchicalConfiguration> nodes = 
                xml.configurationsAt("textBetween");
        for (HierarchicalConfiguration node : nodes) {
            addTextEndpoints(
                    node.getString("[@name]"),
                    node.getString("start", null),
                    node.getString("end", null));
        }
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("tagger");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeAttribute(
                    "caseSensitive", Boolean.toString(isCaseSensitive()));
            writer.writeAttribute("inclusive", Boolean.toString(isInclusive()));
            super.saveToXML(writer);
            for (TextBetween between : betweens) {
                writer.writeStartElement("textBetween");
                writer.writeAttribute("name", between.name);
                writer.writeStartElement("start");
                writer.writeCharacters(between.start);
                writer.writeEndElement();
                writer.writeStartElement("end");
                writer.writeCharacters(between.end);
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

    private class TextBetween implements Comparable<TextBetween>, Serializable {
        private static final long serialVersionUID = 1L;
        private final String name;
        private final String start;
        private final String end;
        public TextBetween(String name, String start, String end) {
            super();
            this.name = name;
            this.start = start;
            this.end = end;
        }
        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof TextBetween)) {
                return false;
            }
            TextBetween castOther = (TextBetween) other;
            return new EqualsBuilder().append(name, castOther.name)
                    .append(start, castOther.start).append(end, castOther.end)
                    .isEquals();
        }
        private transient int hashCode;
        @Override
        public int hashCode() {
            if (hashCode == 0) {
                hashCode = new HashCodeBuilder().append(name).append(start)
                        .append(end).toHashCode();
            }
            return hashCode;
        }
        private transient String toString;
        @Override
        public String toString() {
            if (toString == null) {
                toString = new ToStringBuilder(this).append("name", name)
                        .append("start", start).append("end", end).toString();
            }
            return toString;
        }
        public int compareTo(final TextBetween other) {
            int val = start.length() - end.length();
            if (val != 0) {
                return val;
            }
            return new CompareToBuilder()
                    .append(start, other.start)
                    .append(end, other.end)
                    .append(name, other.name)
                    .toComparison();
        }
    }
}
