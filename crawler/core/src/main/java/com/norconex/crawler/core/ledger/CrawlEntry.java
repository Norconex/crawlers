/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.ledger;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.collection.CollectionUtil;

import lombok.Data;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

/**
 * Holds minimal meta information and state necessary to the proper
 * (re)processing of a document in the context of a crawl.
 * Also persisted in the {@link CrawlEntryLedger}.
 */
@Data
@FieldNameConstants
public class CrawlEntry {

    /** Typically, the number of "hops" or directories to get to a file. */
    private int depth;

    @NonNull
    private ProcessingStatus processingStatus = ProcessingStatus.UNTRACKED;
    private ProcessingOutcome processingOutcome;

    /** Original references before normalization, redirect, etc. */
    private final List<String> referenceTrail = new ArrayList<>();

    @ToString.Exclude
    private String metaChecksum;
    @ToString.Exclude
    private String contentChecksum;
    @ToString.Exclude
    private ZonedDateTime queuedAt;
    @ToString.Exclude
    private ZonedDateTime processingAt;
    @ToString.Exclude
    private ZonedDateTime processedAt;
    private boolean orphan;
    private boolean deleted;
    private String reference;

    public CrawlEntry() {
    }

    public CrawlEntry(String reference) {
        this.reference = reference;
    }

    public CrawlEntry withReference(String reference) {
        var docInfo = BeanUtil.clone(this);
        docInfo.setReference(reference);
        return docInfo;
    }

    /**
     * Gets original references before normalization, redirect, etc.
     * @return reference trail up to, but excluding, the current one.
     */
    public List<String> getReferenceTrail() {
        return Collections.unmodifiableList(referenceTrail);
    }

    /**
     * Sets original references before normalization, redirect, etc.
     * @param referenceTrail reference trail up to, but excluding, the current
     *     one.
     */
    public void setReferenceTrail(List<String> referenceTrail) {
        CollectionUtil.setAll(this.referenceTrail, referenceTrail);
    }

    /**
     * Adds original references before normalization, redirect, etc.
     * @param reference reference to append to the trail
     */
    public void addToReferenceTrail(String reference) {
        referenceTrail.add(reference);
    }

    public String getMetaChecksum() {
        return metaChecksum;
    }

    public void setMetaChecksum(String metaChecksum) {
        this.metaChecksum = metaChecksum;
    }

    /**
     * Gets the content checksum.
     * @return the content checksum
     */
    public String getContentChecksum() {
        return contentChecksum;
    }

    /**
     * Sets the content checksum.
     * @param contentChecksum content checksum
     */
    public void setContentChecksum(String contentChecksum) {
        this.contentChecksum = contentChecksum;
    }

    /**
     * Gets the queued date.
     * @return the queued date
         */
    public ZonedDateTime getQueuedAt() {
        return queuedAt;
    }

    /**
     * Sets the queued date.
     * @param queuedAt the queued date
     */
    public void setQueuedAt(ZonedDateTime queuedAt) {
        this.queuedAt = queuedAt;
    }
}
