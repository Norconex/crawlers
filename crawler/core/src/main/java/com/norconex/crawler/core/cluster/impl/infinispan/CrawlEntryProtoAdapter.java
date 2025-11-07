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
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.infinispan.protostream.annotations.ProtoAdapter;
import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.cluster.CacheException;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.ledger.ProcessingStatus;

@ProtoAdapter(CrawlEntry.class)
public class CrawlEntryProtoAdapter {

    private static final String NULL = "__NULL__";

    @ProtoFactory
    public CrawlEntry create( //NOSONAR
            String type,
            String reference,
            String processingStatus,
            String metaChecksum,
            String contentChecksum,
            long queuedAt,
            long processingAt,
            String processingOutcome,
            Map<String, String> otherProps) {

        if (StringUtils.isBlank(type)) {
            throw new CacheException("Crawl entry type is not defined.");
        }

        try {
            var entry = (CrawlEntry) ClassUtil.newInstance(
                    ClassUtils.getClass(type));
            entry.setReference(nullable(reference));
            entry.setProcessingStatus(
                    nullable(processingStatus, ProcessingStatus::of));
            entry.setMetaChecksum(nullable(metaChecksum));
            entry.setContentChecksum(nullable(contentChecksum));
            entry.setQueuedAt(toZdt(queuedAt));
            entry.setProcessingAt(toZdt(processingAt));
            entry.setProcessingOutcome(ProcessingOutcome.valueOf(
                    nullable(processingOutcome)));
            if (otherProps != null) {
                // Remove empty or placeholder values
                otherProps.entrySet()
                        .removeIf(en -> StringUtils.isEmpty(en.getValue())
                                || NULL.equals(en.getValue()));
                BeanUtils.populate(entry, otherProps);
            }
            return entry;
        } catch (IllegalAccessException | InvocationTargetException
                | ClassNotFoundException e) {
            throw new CacheException(
                    "Could not deserialize crawl entry.", e);
        }
    }

    // Field extractors (indexing handled by POJO annotations on CrawlEntry)
    @ProtoField(1)
    public String getType(CrawlEntry entry) {
        return nullSafe(entry.getClass().getName());
    }

    @ProtoField(2)
    public String getReference(CrawlEntry entry) {
        return nullSafe(entry.getReference());
    }

    @ProtoField(value = 3)
    public String getProcessingStatus(CrawlEntry entry) {
        return ofNullable(entry.getProcessingStatus())
                .map(ProcessingStatus::name)
                .orElse(NULL);
    }

    @ProtoField(4)
    public String getMetaChecksum(CrawlEntry entry) {
        return nullSafe(entry.getMetaChecksum());
    }

    @ProtoField(5)
    public String getContentChecksum(CrawlEntry entry) {
        return nullSafe(entry.getContentChecksum());
    }

    @ProtoField(value = 6)
    public long getQueuedAt(CrawlEntry entry) {
        return toEpoch(entry.getQueuedAt());
    }

    @ProtoField(7)
    public long getProcessingAt(CrawlEntry entry) {
        return toEpoch(entry.getProcessingAt());
    }

    @ProtoField(8)
    public String getProcessingOutcome(CrawlEntry entry) {
        return ofNullable(entry.getProcessingOutcome())
                .map(ProcessingOutcome::toString)
                .orElse(NULL);
    }

    @ProtoField(9)
    public Map<String, String> getOtherProps(CrawlEntry entry) {
        try {
            var props = BeanUtils.describe(entry);
            Stream.of(
                    CrawlEntry.Fields.reference,
                    CrawlEntry.Fields.processingStatus,
                    CrawlEntry.Fields.metaChecksum,
                    CrawlEntry.Fields.contentChecksum,
                    CrawlEntry.Fields.queuedAt,
                    CrawlEntry.Fields.processingOutcome,
                    CrawlEntry.Fields.processingAt).forEach(props::remove);
            // Only replace nulls, not empty strings
            props.replaceAll((k, v) -> v == null ? NULL : v);
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

    private static String nullSafe(String value) {
        return value == null ? NULL : value;
    }

    private static String nullable(String value) {
        return NULL.equals(value) ? null : value;
    }

    private static <T> T nullable(String value, Function<String, T> f) {
        return f.apply(nullable(value));
    }
}