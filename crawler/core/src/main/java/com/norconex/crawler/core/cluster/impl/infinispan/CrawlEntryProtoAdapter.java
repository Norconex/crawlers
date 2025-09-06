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
import org.infinispan.protostream.annotations.ProtoAdapter;
import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.cluster.CacheException;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingStatus;

@ProtoAdapter(CrawlEntry.class)
@Indexed
public class CrawlEntryProtoAdapter {

    @ProtoFactory
    public CrawlEntry create( //NOSONAR
            String type,
            String reference,
            ProcessingStatus processingStatus,
            String metaChecksum,
            String contentChecksum,
            long queuedAt,
            long processingAt,
            Map<String, String> otherProps) {

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

    // Field extractors with indexing
    @ProtoField(1)
    public String getType(CrawlEntry entry) {
        return entry.getClass().getName();
    }

    @ProtoField(2)
    public String getReference(CrawlEntry entry) {
        return entry.getReference();
    }

    @ProtoField(value = 3, name = "processingStatus")
    @GenericField(name = "processingStatus")
    public ProcessingStatus getProcessingStatus(CrawlEntry entry) {
        return entry.getProcessingStatus();
    }

    @ProtoField(4)
    public String getMetaChecksum(CrawlEntry entry) {
        return entry.getMetaChecksum();
    }

    @ProtoField(5)
    public String getContentChecksum(CrawlEntry entry) {
        return entry.getContentChecksum();
    }

    @ProtoField(value = 6, name = "queuedAt")
    @GenericField(name = "queuedAt")
    public long getQueuedAt(CrawlEntry entry) {
        return toEpoch(entry.getQueuedAt());
    }

    @ProtoField(7)
    @GenericField
    public long getProcessingAt(CrawlEntry entry) {
        return toEpoch(entry.getProcessingAt());
    }

    @ProtoField(8)
    public Map<String, String> getOtherProps(CrawlEntry entry) {
        try {
            var props = BeanUtils.describe(entry);
            Stream.of(
                    CrawlEntry.Fields.reference,
                    CrawlEntry.Fields.processingStatus,
                    CrawlEntry.Fields.metaChecksum,
                    CrawlEntry.Fields.contentChecksum,
                    CrawlEntry.Fields.queuedAt,
                    CrawlEntry.Fields.processingAt).forEach(props::remove);
            return props;
        } catch (IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            throw new CacheException("Could not serialize crawl entry.", e);
        }
    }

    // Utility methods
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