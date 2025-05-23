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
package com.norconex.crawler.core.doc;

import java.time.ZonedDateTime;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.importer.doc.DocContext;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/**
 * Holds minimal meta information and state necessary to the proper
 * (re)processing of a document in the context of a crawl.
 * Also persisted in grid storage as a {@link CrawlDocLedger} entry.
 */
@EqualsAndHashCode
public class CrawlDocContext extends DocContext {
    private static final long serialVersionUID = 1L;

    // for crawlers that support this notion
    private int depth;

    @Setter
    @Getter
    @NonNull
    private CrawlDocStage processingStage = CrawlDocStage.NONE;

    private String originalReference; //TODO keep the trail if it changes often?

    @ToString.Exclude
    private String parentRootReference;
    private CrawlDocStatus resolutionStatus;
    @ToString.Exclude
    private String metaChecksum;
    @ToString.Exclude
    private String contentChecksum;
    @ToString.Exclude
    private ZonedDateTime crawlDate;
    private ZonedDateTime lastModified;
    @Setter
    @Getter
    private boolean orphan;
    @Setter
    @Getter
    private boolean deleted;

    public CrawlDocContext() {
    }

    public CrawlDocContext(String reference) {
        super(reference);
    }

    /**
     * Copy constructor.
     * @param docDetails document details to copy
     */
    public CrawlDocContext(DocContext docDetails) {
        super(docDetails);
    }

    public CrawlDocContext withReference(String reference) {
        var docInfo = BeanUtil.clone(this);
        docInfo.setReference(reference);
        return docInfo;
    }

    /**
     * Gets the document location depth.
     * @return document location depth
     */
    public int getDepth() {
        return depth;
    }

    /**
     * Sets the document location depth.
     * @param depth document location depth
     */
    public final void setDepth(int depth) {
        this.depth = depth;
    }

    public String getOriginalReference() {
        return originalReference;
    }

    public void setOriginalReference(String originalReference) {
        this.originalReference = originalReference;
    }

    //TODO Get rid of parentRootReference? (not used?)
    // Store an embedded trail instead?
    public String getParentRootReference() {
        return parentRootReference;
    }

    public void setParentRootReference(String parentRootReference) {
        this.parentRootReference = parentRootReference;
    }

    public CrawlDocStatus getState() {
        return resolutionStatus;
    }

    public void setState(CrawlDocStatus state) {
        resolutionStatus = state;
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
     * Gets the crawl date.
     * @return the crawl date
         */
    public ZonedDateTime getCrawlDate() {
        return crawlDate;
    }

    /**
     * Sets the crawl date.
     * @param crawlDate the crawl date
     */
    public void setCrawlDate(ZonedDateTime crawlDate) {
        this.crawlDate = crawlDate;
    }

    public ZonedDateTime getLastModified() {
        return lastModified;
    }

    public void setLastModified(ZonedDateTime lastModified) {
        this.lastModified = lastModified;
    }
}
