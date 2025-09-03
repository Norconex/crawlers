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
package com.norconex.crawler.core.cluster.impl.infinispan;

import static java.util.Optional.ofNullable;

import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.infinispan.protostream.annotations.Proto;
import org.infinispan.protostream.annotations.ProtoField;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.cluster.CacheException;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingStatus;

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

    @ProtoField(number = 3)
    @GenericField
    public ProcessingStatus processingStatus;

    @ProtoField(number = 4)
    public String metaChecksum;

    @ProtoField(number = 5)
    public String contentChecksum;

    @ProtoField(number = 6)
    @GenericField
    public long queuedAt;

    @ProtoField(number = 7)
    @GenericField
    public long processingAt;

    @ProtoField(number = 8)
    public Map<String, String> otherProps;

    public CrawlEntryProtoAdapter() {
    }

    public CrawlEntryProtoAdapter(CrawlEntry entry) {
        try {
            type = entry.getClass().getName();
            reference = entry.getReference();
            processingStatus = entry.getProcessingStatus();
            metaChecksum = entry.getMetaChecksum();
            contentChecksum = entry.getContentChecksum();
            queuedAt = toEpoch(entry.getQueuedAt());
            processingAt = toEpoch(entry.getProcessingAt());
            var props = BeanUtils.describe(entry);
            Stream.of(
                    CrawlEntry.Fields.reference,
                    CrawlEntry.Fields.processingStatus,
                    CrawlEntry.Fields.metaChecksum,
                    CrawlEntry.Fields.contentChecksum,
                    CrawlEntry.Fields.queuedAt,
                    CrawlEntry.Fields.processingAt).forEach(props::remove);
            otherProps = props;
        } catch (IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            throw new CacheException("Could not serialize crawl entry.", e);
        }

    }

    public CrawlEntry toCrawlEntry() {
        if (StringUtils.isBlank(type)) {
            throw new CacheException("Crawl entry type is not defined.");
        }
        try {
            var entry = (CrawlEntry) ClassUtil.newInstance(
                    ClassUtils.getClass(type));
            entry.setReference(reference);
            entry.setProcessingStatus(processingStatus);
            entry.setMetaChecksum(metaChecksum);
            entry.setContentChecksum(contentChecksum);
            entry.setQueuedAt(toZdt(queuedAt));
            entry.setProcessingAt(toZdt(processingAt));
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

    private static long toEpoch(ZonedDateTime zdt) {
        return ofNullable(zdt)
                .map(dt -> dt.toInstant().toEpochMilli())
                .orElse(0L);
    }

    private static ZonedDateTime toZdt(long epoch) {
        return epoch <= 0 ? null
                : Instant.ofEpochMilli(epoch).atZone(ZoneOffset.UTC);
    }
}
