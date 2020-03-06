/* Copyright 2020 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.http.link;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.parser.ParseState;

/**
 * <p>
 * Base class for link extraction providing common configuration settings.
 * </p>
 *
 * <p>
 * Subclasses inherit the following:
 * </p>
 *
 * {@nx.xml.usage
 * {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * }
 *
 * {@nx.xml.example
 * {@nx.include com.norconex.importer.handler.AbstractImporterHandler@nx.xml.example}
 * }
 * <p>
 * The above will apply to any content type starting with "text/".
 * </p>
 *
 * @author Pascal Essiembre
 * @since 3.0.0
 */
@SuppressWarnings("javadoc")
public abstract class AbstractLinkExtractor
        implements ILinkExtractor, IXMLConfigurable {


    //TODO add support for using fields to find xml.

    private static final Logger LOG = LoggerFactory.getLogger(
            AbstractLinkExtractor.class);

    private final PropertyMatchers restrictions = new PropertyMatchers();

    @Override
    public final Set<Link> extractLinks(CrawlDoc doc, ParseState parseState)
            throws IOException {
        if (!acceptParseState(parseState)) {
            throw new CollectorException(
                    "This link extractor cannot be used when parse state is "
                    + parseState + ": " + this.getClass().getSimpleName());
        }
        Set<Link> links = new HashSet<>();
        if (restrictions.isEmpty() || restrictions.matches(doc.getMetadata())) {
            extractLinks(links, doc, parseState);
        } else {
            LOG.debug("{} extractor does not apply to: {} (preImport={}).",
                    getClass(), doc.getReference(), parseState);
        }
        return links;
    }

    public abstract void extractLinks(
            Set<Link> links, CrawlDoc doc, ParseState parseState)
                    throws IOException;

    /**
     * Subclasses can override this method to enforce usage of this extractor
     * with certain parsing states.
     * Default implementation accepts any parse state.
     * @param parseState parse state
     * @return <code>true</code> if accepting for parse state.
     */
    protected boolean acceptParseState(ParseState parseState) {
        return true;
    }

    /**
     * Adds one or more restrictions this extractor should be restricted to.
     * @param restrictions the restrictions
     */
    public void addRestriction(PropertyMatcher... restrictions) {
        if (restrictions != null) {
            this.restrictions.addAll(restrictions);
        }
    }
    /**
     * Adds restrictions this extractor should be restricted to.
     * @param restrictions the restrictions
     */
    public void addRestrictions(List<PropertyMatcher> restrictions) {
        if (restrictions != null) {
            this.restrictions.addAll(restrictions);
        }
    }
    /**
     * Sets restrictions this extractor should be restricted to.
     * @param restrictions the restrictions
     */
    public void setRestrictions(List<PropertyMatcher> restrictions) {
        CollectionUtil.setAll(this.restrictions, restrictions);
    }
    /**
     * Removes all restrictions on a given field.
     * @param field the field to remove restrictions on
     * @return how many elements were removed
     */
    public int removeRestriction(String field) {
        return restrictions.remove(field);
    }
    /**
     * Removes a restriction.
     * @param restriction the restriction to remove
     * @return <code>true</code> if this extractor contained the restriction
     */
    public boolean removeRestriction(PropertyMatcher restriction) {
        return restrictions.remove(restriction);
    }
    /**
     * Clears all restrictions.
     */
    public void clearRestrictions() {
        restrictions.clear();
    }
    /**
     * Gets all restrictions
     * @return the restrictions
     */
    public PropertyMatchers getRestrictions() {
        return restrictions;
    }

    @Override
    public final void loadFromXML(XML xml) {
        loadLinkExtractorFromXML(xml);
        List<XML> nodes = xml.getXMLList("restrictTo");
        if (!nodes.isEmpty()) {
            restrictions.clear();
            for (XML node : nodes) {
                restrictions.add(PropertyMatcher.loadFromXML(node));
            }
        }
    }
    /**
     * Loads configuration settings specific to the implementing class.
     * @param xml XML configuration
     */
    protected abstract void loadLinkExtractorFromXML(XML xml);

    @Override
    public final void saveToXML(XML xml) {
        saveLinkExtractorToXML(xml);
        restrictions.forEach(pm -> {
            PropertyMatcher.saveToXML(xml.addElement("restrictTo"), pm);
        });
    }

    /**
     * Saves configuration settings specific to the implementing class.
     * @param xml the XML
     */
    protected abstract void saveLinkExtractorToXML(XML xml);

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
