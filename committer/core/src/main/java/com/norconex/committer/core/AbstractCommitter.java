/* Copyright 2020-2022 Norconex Inc.
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
package com.norconex.committer.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.commons.collections4.map.ListOrderedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.event.Level;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;

import lombok.EqualsAndHashCode;
import lombok.NonNull;

/**
 * <p>
 * A base implementation taking care of basic plumbing, such as
 * firing main Committer events (including exceptions),
 * storing the Committer context (available via {@link #getCommitterContext()}),
 * and adding support for filtering unwanted requests.
 * </p>
 *
 * {@nx.block #restrictTo
 * <h3>Restricting committer to specific documents</h3>
 * <p>
 * Optionally apply a committer only to certain type of documents.
 * Documents are restricted based on their
 * metadata field names and values. This option can be used to
 * perform document routing when you have multiple committers defined.
 * </p>
 * }
 *
 * {@nx.block #fieldMappings
 * <h3>Field mappings</h3>
 * <p>
 * By default, this abstract class applies field mappings for metadata fields,
 * but leaves the document reference and content (input stream) for concrete
 * implementations to handle. In other words, they only apply to
 * a committer request metadata.
 * Field mappings are performed on committer requests before upserts and
 * deletes are actually performed.
 * </p>
 * }
 *
 * {@nx.xml.usage
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
 * <fieldMappings>
 *   <!-- Add as many field mappings as needed -->
 *   <mapping fromField="(source field name)" toField="(target field name)"/>
 * </fieldMappings>
 * }
 * <p>
 * Implementing classes inherit the above XML configuration.
 * </p>
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
public abstract class AbstractCommitter
        implements Committer, XMLConfigurable {

    private CommitterContext committerContext;
    private final PropertyMatchers restrictions = new PropertyMatchers();
    private final Map<String, String> fieldMappings = new ListOrderedMap<>();

    /**
     * Adds one restriction this committer should be restricted to.
     * @param restriction the restriction
     */
    public void addRestriction(PropertyMatcher restriction) {
        restrictions.add(restriction);
    }
    /**
     * Adds restrictions this committer should be restricted to.
     * @param restrictions the restrictions
     */
    public void addRestrictions(List<PropertyMatcher> restrictions) {
        if (restrictions != null) {
            this.restrictions.addAll(restrictions);
        }
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
     * @return <code>true</code> if this committer contained the restriction
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

    /**
     * Gets an unmodifiable copy of the metadata mappings.
     * @return metadata mappings
     */
    public Map<String, String> getFieldMappings() {
        return Collections.unmodifiableMap(fieldMappings);
    }
    /**
     * Sets a metadata field mapping.
     * @param fromField source field
     * @param toField target field
     */
    public void setFieldMapping(String fromField, String toField) {
        fieldMappings.put(fromField, toField);
    }
    /**
     * Sets a metadata field mappings, where the key is the source field and
     * the value is the target field.
     * @param mappings metadata field mappings
     */
    public void setFieldMappings(Map<String, String> mappings) {
        fieldMappings.putAll(mappings);
    }
    public String removeFieldMapping(String fromField) {
        return fieldMappings.remove(fromField);
    }
    public void clearFieldMappings() {
        fieldMappings.clear();
    }

    @Override
    public final void init(@NonNull CommitterContext committerContext)
            throws CommitterException {
        this.committerContext = committerContext;
        fireInfo(CommitterEvent.COMMITTER_INIT_BEGIN);
        try {
            doInit();
        } catch (CommitterException | RuntimeException e) {
            fireError(CommitterEvent.COMMITTER_INIT_ERROR, e);
            throw e;
        }
        fireInfo(CommitterEvent.COMMITTER_INIT_END);
    }

    @Override
    public boolean accept(CommitterRequest request) throws CommitterException {
        try {
            if (restrictions.isEmpty()
                    || restrictions.matches(request.getMetadata())) {
                fireInfo(CommitterEvent.COMMITTER_ACCEPT_YES);
                return true;
            }
            fireInfo(CommitterEvent.COMMITTER_ACCEPT_NO);
        } catch (RuntimeException e) {
            fireError(CommitterEvent.COMMITTER_ACCEPT_ERROR, e);
            throw e;
        }
        return false;
    }

    @Override
    public final void upsert(
            UpsertRequest upsertRequest) throws CommitterException {
        fireInfo(CommitterEvent.COMMITTER_UPSERT_BEGIN, upsertRequest);
        try {
            applyFieldMappings(upsertRequest);
            doUpsert(upsertRequest);
        } catch (CommitterException | RuntimeException e) {
            fireError(CommitterEvent.COMMITTER_UPSERT_ERROR, upsertRequest, e);
            throw e;
        }
        fireInfo(CommitterEvent.COMMITTER_UPSERT_END, upsertRequest);
    }
    @Override
    public final void delete(
            DeleteRequest deleteRequest) throws CommitterException {
        fireInfo(CommitterEvent.COMMITTER_DELETE_BEGIN, deleteRequest);
        try {
            applyFieldMappings(deleteRequest);
            doDelete(deleteRequest);
        } catch (CommitterException | RuntimeException e) {
            fireError(CommitterEvent.COMMITTER_DELETE_ERROR, deleteRequest, e);
            throw e;
        }
        fireInfo(CommitterEvent.COMMITTER_DELETE_END, deleteRequest);
    }

    protected void applyFieldMappings(CommitterRequest req) {
        var props = new Properties();
        for (Entry<String, List<String>> en : req.getMetadata().entrySet()) {
            var fromField = en.getKey();
            if (fieldMappings.containsKey(fromField)) {
                var toField = fieldMappings.get(fromField);
                // if target undefined, do not set
                if (StringUtils.isNotBlank(toField)) {
                    props.addList(toField, en.getValue());
                }
            } else {
                props.addList(fromField, en.getValue());
            }
        }
        req.getMetadata().clear();
        req.getMetadata().putAll(props);
    }

    @Override
    public final void close() throws CommitterException {
        fireInfo(CommitterEvent.COMMITTER_CLOSE_BEGIN);
        try {
            doClose();
        } catch (CommitterException | RuntimeException e) {
            fireError(CommitterEvent.COMMITTER_CLOSE_ERROR, e);
            throw e;
        }
        fireInfo(CommitterEvent.COMMITTER_CLOSE_END);
    }

    @Override
    public final void clean() throws CommitterException {
        fireInfo(CommitterEvent.COMMITTER_CLEAN_BEGIN);
        try {
            doClean();
        } catch (CommitterException | RuntimeException e) {
            fireError(CommitterEvent.COMMITTER_CLEAN_ERROR, e);
            throw e;
        }
        fireInfo(CommitterEvent.COMMITTER_CLEAN_END);
    }


    public CommitterContext getCommitterContext() {
        return committerContext;
    }

    /**
     * Subclasses can perform additional initialization by overriding this
     * method. Default implementation does nothing. The
     * {@link CommitterContext} will be initialized when invoking
     * {@link #getCommitterContext()}
     * @throws CommitterException error initializing
     */
    protected abstract void doInit()
            throws CommitterException;

    protected abstract void doUpsert(UpsertRequest upsertRequest)
            throws CommitterException;

    protected abstract void doDelete(DeleteRequest deleteRequest)
            throws CommitterException;
    /**
     * Subclasses can perform additional closing logic by overriding this
     * method. Default implementation does nothing.
     * @throws CommitterException error closing committer
     */
    protected abstract void doClose() throws CommitterException;

    protected abstract void doClean() throws CommitterException;

    protected final void fireDebug(String name) {
        fireInfo(name, null);
    }
    protected final void fireDebug(String name, CommitterRequest req) {
        fire(CommitterEvent.builder()
                .name(name)
                .source(this)
                .request(req)
                .build(),
                Level.DEBUG);
    }
    protected final void fireInfo(String name) {
        fireInfo(name, null);
    }
    protected final void fireInfo(String name, CommitterRequest req) {
        fire(CommitterEvent.builder()
                .name(name)
                .source(this)
                .request(req)
                .build(),
                Level.INFO);
    }
    protected final void fireError(String name, Exception e) {
        fireError(name, null, e);
    }
    protected final void fireError(
            String name, CommitterRequest req, Exception e) {
        fire(CommitterEvent.builder()
                .name(name)
                .source(this)
                .request(req)
                .exception(e)
                .build(),
                Level.ERROR);
    }
    private void fire(CommitterEvent e, Level level) {
        Optional.ofNullable(committerContext)
            .map(CommitterContext::getEventManager)
            .ifPresent(em -> em.fire(e, level));
    }

    @Override
    public final void loadFromXML(XML xml) {
        loadCommitterFromXML(xml);
        var nodes = xml.getXMLList("restrictTo");
        if (!nodes.isEmpty()) {
            restrictions.clear();
            for (XML node : nodes) {
                node.checkDeprecated("@field", "fieldMatcher", true);
                restrictions.add(PropertyMatcher.loadFromXML(node));
            }
        }

        var xmlMappings = xml.getXMLList("fieldMappings/mapping");
        for (XML xmlMapping : xmlMappings) {
            setFieldMapping(
                    xmlMapping.getString("@fromField", null),
                    xmlMapping.getString("@toField", null));
        }
    }
    @Override
    public final void saveToXML(XML xml) {
        saveCommitterToXML(xml);
        restrictions.forEach(pm ->
                PropertyMatcher.saveToXML(xml.addElement("restrictTo"), pm));

        var fieldsXml = xml.addElement("fieldMappings");
        for (Entry<String, String> en : fieldMappings.entrySet()) {
            fieldsXml.addElement("mapping")
                    .setAttribute("fromField", en.getKey())
                    .setAttribute("toField", en.getValue());
        }
    }

    public abstract void loadCommitterFromXML(XML xml);
    public abstract void saveCommitterToXML(XML xml);

    @Override
    public String toString() {
        // Cannot use ReflectionToStringBuilder here to prevent
        // "An illegal reflective access operation has occurred"
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("committerContext", committerContext)
                .append("restrictions", restrictions)
                .append("fieldMappings", fieldMappings)
                .build();
    }
}