/* Copyright 2019-2022 Norconex Inc.
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

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringExclude;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.importer.doc.DocRecord;

import lombok.EqualsAndHashCode;

//@JsonAutoDetect(
//    getterVisibility = JsonAutoDetect.Visibility.NONE,
//    isGetterVisibility = JsonAutoDetect.Visibility.NONE
//)
@EqualsAndHashCode
public class CrawlDocRecord extends DocRecord {

    //TODO create @ignore metadata to prevent storing some fields?

    private static final long serialVersionUID = 1L;

    //TODO make this a top-level enum.
    public enum Stage {
        QUEUED, ACTIVE, PROCESSED /*, CACHED*/;

        public boolean is(Stage stage) {
            return stage != null && stage == this;
        }

    } //TODO add NONE?

    // for crawlers that support this notion
    private int depth;

    private String originalReference; //TODO keep the trail if it changes often?

    @ToStringExclude
    private String parentRootReference;
    private CrawlDocState state;
    @ToStringExclude
    private String metaChecksum;
    @ToStringExclude
    private String contentChecksum;
    @ToStringExclude
    private ZonedDateTime crawlDate;

    public CrawlDocRecord() {}

    public CrawlDocRecord(String reference) {
        super(reference);
    }
    /**
     * Copy constructor.
     * @param docDetails document details to copy
     */
    public CrawlDocRecord(DocRecord docDetails) {
        super(docDetails);
    }

    public CrawlDocRecord withReference(String reference) {
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

    public CrawlDocState getState() {
        return state;
    }
    public void setState(CrawlDocState state) {
        this.state = state;
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

    @Override
    public String toString() {
        var b = new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE);
        b.setExcludeNullValues(true);
        return b.toString();

    }
}
