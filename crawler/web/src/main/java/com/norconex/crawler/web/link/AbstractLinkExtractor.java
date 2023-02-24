/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.crawler.web.link;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.doc.CrawlDoc;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

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
 * The above example will apply to any content type starting with "text/".
 * </p>
 *
 * @since 3.0.0
 */
@SuppressWarnings("javadoc")
@Slf4j
@EqualsAndHashCode
@ToString
public abstract class AbstractLinkExtractor
        implements LinkExtractor, XMLConfigurable {

    private final PropertyMatchers restrictions = new PropertyMatchers();

    @Override
    public final Set<Link> extractLinks(CrawlDoc doc) throws IOException {
        Set<Link> links = new HashSet<>();
        if (restrictions.isEmpty() || restrictions.matches(doc.getMetadata())) {
            extractLinks(links, doc);
        } else {
            LOG.debug("{} extractor does not apply to: {}).",
                    getClass(), doc.getReference());
        }
        return links;
    }

    public abstract void extractLinks(Set<Link> links, CrawlDoc doc)
            throws IOException;

    /**
     * Adds one or more restrictions this extractor should be restricted to.
     * @param restrictions the restrictions
     */
    public void addRestriction(PropertyMatcher restriction) {
        if (restriction != null) {
            restrictions.add(restriction);
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
        var nodes = xml.getXMLList("restrictTo");
        if (!nodes.isEmpty()) {
            restrictions.clear();
            for (XML node : nodes) {
                Object obj = PropertyMatcher.loadFromXML(node);
                Optional.ofNullable(PropertyMatcher.loadFromXML(node))
                    .ifPresent(r -> restrictions.add(r));
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
        if (restrictions.isEmpty()) {
            xml.addElement("restrictTo");
        } else {
            restrictions.forEach(pm ->
                PropertyMatcher.saveToXML(xml.addElement("restrictTo"), pm)
            );
        }
    }

    /**
     * Saves configuration settings specific to the implementing class.
     * @param xml the XML
     */
    protected abstract void saveLinkExtractorToXML(XML xml);
}
