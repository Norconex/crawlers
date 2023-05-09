/* Copyright 2010-2022 Norconex Inc.
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
package com.norconex.importer.handler;

import java.util.List;

import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for handlers applying only to certain type of documents
 * by providing a way to restrict applicable documents based on
 * a metadata field value, where the value matches a regular expression. For
 * instance, to apply a handler only to text documents, you can use the
 * following:
 *
 * <pre>
 *   myHandler.setRestriction(new PropertyMatcher("document.contentType",
 *          new TextMatcher(Method.REGEX).setPattern("^text/.*$")));
 * </pre>
 *
 * <p>
 * Subclasses <b>must</b> test if a document is accepted using the
 * {@link #isApplicable(HandlerDoc, ParseState)} method.
 * </p>
 * <p>
 * Subclasses can safely be used as either pre-parse or post-parse handlers.
 * </p>
 *
 * {@nx.xml.usage #restrictTo
 * <!-- multiple "restrictTo" tags allowed (only one needs to match) -->
 * <restrictTo>
 *   <fieldMatcher
 *     {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (field-matching expression)
 *   </fieldMatcher>
 *   <valueMatcher
 *     {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (value-matching expression)
 *   </valueMatcher>
 * </restrictTo>
 * }
 * <p>
 * Subclasses inherit the above {@link XMLConfigurable} configuration.
 * </p>
 *
 * {@nx.xml.example
 * <restrictTo>
 *   <fieldMatcher>document.contentType</fieldMatcher>
 *   <valueMatcher method="wildcard">
 *     text/*
 *   </valueMatcher>
 * </restrictTo>
 * }
 * <p>
 * The above will apply to any content type starting with "text/".
 * </p>
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class AbstractImporterHandler implements XMLConfigurable {

    //TODO consider having a more generic base class that has just restrictTo
    // e.g., AbstractRetrictToConfigurable or something like that.

    private final PropertyMatchers restrictions = new PropertyMatchers();

    /**
     * Adds one or more restrictions this handler should be restricted to.
     * @param restrictions the restrictions
         */
    public synchronized void addRestriction(PropertyMatcher... restrictions) {
        this.restrictions.addAll(restrictions);
    }
    /**
     * Adds restrictions this handler should be restricted to.
     * @param restrictions the restrictions
         */
    public synchronized void addRestrictions(
            List<PropertyMatcher> restrictions) {
        if (restrictions != null) {
            this.restrictions.addAll(restrictions);
        }
    }

    /**
     * Removes all restrictions on a given field.
     * @param field the field to remove restrictions on
     * @return how many elements were removed
         */
    public synchronized  int removeRestriction(String field) {
        return restrictions.remove(field);
    }

    /**
     * Removes a restriction.
     * @param restriction the restriction to remove
     * @return <code>true</code> if this handler contained the restriction
         */
    public synchronized boolean removeRestriction(PropertyMatcher restriction) {
        return restrictions.remove(restriction);
    }

    /**
     * Clears all restrictions.
         */
    public synchronized void clearRestrictions() {
        restrictions.clear();
    }

    /**
     * Gets all restrictions
     * @return the restrictions
         */
    public PropertyMatchers getRestrictions() {
        return restrictions;
    }

    /**
     * Class to invoke by subclasses to find out if this handler should be
     * rejected or not based on the metadata restriction provided.
     * @param doc document
     * @param parseState if the document was parsed (i.e. imported) already
     * @return <code>true</code> if this handler is applicable to the document
     */
    protected final boolean isApplicable(
            HandlerDoc doc, ParseState parseState) {
        if (restrictions.isEmpty() || restrictions.matches(doc.getMetadata())) {
            return true;
        }
        LOG.debug("{} handler does not apply to: {} (parsed={}).",
                getClass(), doc.getReference(), parseState);
        return false;
    }

    @Override
    public final void loadFromXML(XML xml) {
        loadHandlerFromXML(xml);
        var nodes = xml.getXMLList("restrictTo");
        if (!nodes.isEmpty()) {
            restrictions.clear();
            for (XML node : nodes) {
                node.checkDeprecated("@field", "fieldMatcher", true);
                restrictions.add(PropertyMatcher.loadFromXML(node));
            }
        }
    }
    /**
     * Loads configuration settings specific to the implementing class.
     * @param xml XML configuration
     */
    protected abstract void loadHandlerFromXML(XML xml);

    @Override
    public void saveToXML(XML xml) {
        saveHandlerToXML(xml);
        restrictions.forEach(pm -> {
            PropertyMatcher.saveToXML(xml.addElement("restrictTo"), pm);
        });
    }

    /**
     * Saves configuration settings specific to the implementing class.
     * @param xml the XML
     */
    protected abstract void saveHandlerToXML(XML xml);
}
