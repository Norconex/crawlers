/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.committer.core.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.committer.core.AbstractCommitter;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.map.Properties;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * <b>WARNING: Not intended for production use.</b>
 * </p>
 * <p>
 * A Committer that stores every document received into memory.
 * This can be useful for testing or troubleshooting applications using
 * Committers.
 * </p>
 * <p>
 * Given this committer can eat up memory pretty quickly, its <b>use is
 * strongly discouraged</b> for regular production use.
 * </p>
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class MemoryCommitter extends AbstractCommitter<MemoryCommitterConfig> {

    //TODO make requests static and using thread local

    private final List<CommitterRequest> requests = new ArrayList<>();

    private int upsertCount = 0;
    private int deleteCount = 0;
    @JsonIgnore
    @Getter
    @Setter
    private boolean closed;

    private final MemoryCommitterConfig configuration =
            new MemoryCommitterConfig();

    @Override
    public MemoryCommitterConfig getConfiguration() {
        return configuration;
    }

    @Override
    protected void doInit() {
        //NOOP
    }

    public boolean removeRequest(CommitterRequest req) {
        return requests.remove(req);
    }

    @JsonIgnore
    public List<CommitterRequest> getAllRequests() {
        return requests;
    }

    @JsonIgnore
    public List<UpsertRequest> getUpsertRequests() {
        return requests.stream()
                .filter(UpsertRequest.class::isInstance)
                .map(UpsertRequest.class::cast)
                .toList();
    }

    @JsonIgnore
    public List<DeleteRequest> getDeleteRequests() {
        return requests.stream()
                .filter(DeleteRequest.class::isInstance)
                .map(DeleteRequest.class::cast)
                .toList();
    }

    @JsonIgnore
    public int getUpsertCount() {
        return upsertCount;
    }

    @JsonIgnore
    public int getDeleteCount() {
        return deleteCount;
    }

    @JsonIgnore
    public int getRequestCount() {
        return requests.size();
    }

    @Override
    protected void doUpsert(UpsertRequest upsertRequest)
            throws CommitterException {
        var memReference = upsertRequest.getReference();
        LOG.debug("Committing upsert request for {}", memReference);

        InputStream memContent = null;
        var reqContent = upsertRequest.getContent();
        if (!configuration.isIgnoreContent() && reqContent != null) {
            try {
                memContent = new ByteArrayInputStream(
                        IOUtils.toByteArray(reqContent));
            } catch (IOException e) {
                throw new CommitterException(
                        "Could not do upsert for " + memReference);
            }
        }

        var memMetadata = filteredMetadata(upsertRequest.getMetadata());

        requests.add(new UpsertRequest(memReference, memMetadata, memContent));
        upsertCount++;
    }

    @Override
    protected void doDelete(DeleteRequest deleteRequest) {
        var memReference = deleteRequest.getReference();
        LOG.debug("Committing delete request for {}", memReference);
        var memMetadata = filteredMetadata(deleteRequest.getMetadata());
        requests.add(new DeleteRequest(memReference, memMetadata));
        deleteCount++;
    }

    private Properties filteredMetadata(Properties reqMetadata) {
        var memMetadata = new Properties();
        if (reqMetadata != null) {

            if (configuration.getFieldMatcher().getPattern() == null) {
                memMetadata.loadFromMap(reqMetadata);
            } else {
                memMetadata.loadFromMap(
                        reqMetadata.entrySet().stream()
                                .filter(
                                        en -> configuration.getFieldMatcher()
                                                .matches(en.getKey()))
                                .collect(
                                        Collectors.toMap(
                                                Entry::getKey,
                                                Entry::getValue)));
            }
        }
        return memMetadata;
    }

    @Override
    protected void doClose()
            throws com.norconex.committer.core.CommitterException {
        LOG.info("{} upserts committed.", upsertCount);
        LOG.info("{} deletions committed.", deleteCount);
        closed = true;
    }

    @Override
    protected void doClean() throws CommitterException {
        requests.clear();
        upsertCount = 0;
        deleteCount = 0;
    }

    @Override
    public String toString() {
        // Cannot use ReflectionToStringBuilder here to prevent
        // "An illegal reflective access operation has occurred"
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .appendSuper(super.toString())
                .append("requests", requests, false)
                .append("upsertCount", upsertCount)
                .append("deleteCount", deleteCount)
                .build();
    }
}
