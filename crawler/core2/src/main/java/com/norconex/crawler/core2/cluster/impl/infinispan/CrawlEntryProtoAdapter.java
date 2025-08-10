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
package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoField;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core2.cluster.CacheException;
import com.norconex.crawler.core2.ledger.CrawlEntry;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Protocol Buffers adapter for CrawlEntry serialization in Infinispan.
 * Only essential fields are serialized directly as ProtoFields, while
 * the rest are stored in a metadata map.
 */
@Proto
@Indexed
@EqualsAndHashCode
@ToString
public class CrawlEntryProtoAdapter {

    @ProtoField(number = 1)
    public String type;

    @ProtoField(number = 2)
    public String reference;

    //    @GenericField
    //    private ProcessingStatus processingStatus;

    @ProtoField(number = 3)
    public String metaChecksum;

    @ProtoField(number = 4)
    public String contentChecksum;

    //    @GenericField
    //    private ZonedDateTime queuedAt;

    @ProtoField(number = 5)
    public Map<String, String> otherProps;

    public CrawlEntryProtoAdapter() {
    }

    public CrawlEntryProtoAdapter(CrawlEntry entry) {
        try {
            type = entry.getClass().getName();
            reference = entry.getReference();
            //            processingStatus = entry.getProcessingStatus();
            metaChecksum = entry.getMetaChecksum();
            contentChecksum = entry.getContentChecksum();
            //            queuedAt = entry.getQueuedAt();
            var props = BeanUtils.describe(entry);
            Stream.of(
                    CrawlEntry.Fields.reference,
                    CrawlEntry.Fields.processingStatus,
                    CrawlEntry.Fields.metaChecksum,
                    CrawlEntry.Fields.queuedAt,
                    CrawlEntry.Fields.contentChecksum).forEach(props::remove);
            otherProps = props;
        } catch (IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            throw new CacheException("Could not serialize crawl entry.", e);
        }

    }

    CrawlEntry toCrawlEntry() {
        if (StringUtils.isBlank(type)) {
            throw new CacheException("Crawl entry type is not defined.");
        }
        try {
            var entry = (CrawlEntry) ClassUtil.newInstance(
                    ClassUtils.getClass(type));
            entry.setReference(reference);
            //            entry.setProcessingStatus(processingStatus);
            entry.setMetaChecksum(metaChecksum);
            entry.setContentChecksum(contentChecksum);
            //            entry.setQueuedAt(queuedAt);
            if (otherProps != null) {
                BeanUtils.populate(entry, otherProps);
            }
            return entry;
        } catch (IllegalAccessException | InvocationTargetException
                | ClassNotFoundException e) {
            throw new CacheException(
                    "Could not deserialize crawl entry.", e);
        }
    }

    //    @ProtoField(number = 1)
    //    public String getType() {
    //        return type;
    //    }
    //
    //    public void setType(String type) {
    //        this.type = type;
    //    }
    //
    //    @ProtoField(number = 2)
    //    public String getReference() {
    //        return reference;
    //    }
    //
    //    public void setReference(String reference) {
    //        this.reference = reference;
    //    }
    //
    //    //    @ProtoField(number = 3)
    //    //    public ProcessingStatus getProcessingStatus() {
    //    //        return processingStatus;
    //    //    }
    //    //    public void setProcessingStatus(ProcessingStatus processingStatus) {
    //    //        this.processingStatus = processingStatus;
    //    //    }
    //
    //    @ProtoField(number = 3)
    //    public String getMetaChecksum() {
    //        return metaChecksum;
    //    }
    //
    //    public void setMetaChecksum(String metaChecksum) {
    //        this.metaChecksum = metaChecksum;
    //    }
    //
    //    @ProtoField(number = 4)
    //    public String getContentChecksum() {
    //        return contentChecksum;
    //    }
    //
    //    public void setContentChecksum(String contentChecksum) {
    //        this.contentChecksum = contentChecksum;
    //    }
    //
    //    //    @ProtoField(number = 6)
    //    //    public ZonedDateTime getQueuedAt() {
    //    //        return queuedAt;
    //    //    }
    //    //    public void setQueuedAt(ZonedDateTime queuedAt) {
    //    //        this.queuedAt = queuedAt;
    //    //    }
    //
    //    @ProtoField(number = 5)
    //    public Map<String, String> getOtherProps() {
    //        return otherProps;
    //    }
    //
    //    public void setOtherProps(Map<String, String> otherProps) {
    //        this.otherProps = otherProps;
    //    }
}
