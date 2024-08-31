/* Copyright 2020-2024 Norconex Inc.
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

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.event.Level;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.Properties;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;

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
 * @param <T> The type of the committer configuration class
 */
@EqualsAndHashCode
@ToString
public abstract class AbstractCommitter<T extends BaseCommitterConfig>
        implements Committer, Configurable<T> {

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    private CommitterContext committerContext;

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
            if (getConfiguration().getRestrictions().isEmpty()
                    || getConfiguration().getRestrictions().matches(
                            request.getMetadata())) {
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
            if (getConfiguration().getFieldMappings().containsKey(fromField)) {
                var toField = getConfiguration().getFieldMappings().get(
                        fromField);
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
        fire(
                CommitterEvent.builder()
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
        fire(
                CommitterEvent.builder()
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
        fire(
                CommitterEvent.builder()
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
}