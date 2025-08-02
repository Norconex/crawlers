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
import org.infinispan.protostream.annotations.ProtoAdapter;
import org.infinispan.protostream.annotations.ProtoField;

import com.norconex.crawler.core2.cluster.CacheException;
import com.norconex.crawler.core2.ledger.CrawlEntry;
import com.norconex.crawler.core2.ledger.ProcessingStatus;

/**
 * Protocol Buffers adapter for CrawlEntry serialization in Infinispan.
 * Only essential fields are serialized directly as ProtoFields, while
 * the rest are stored in a metadata map.
 */
@ProtoAdapter(CrawlEntry.class)
public class CrawlEntryProtoAdapter {

    @ProtoField(number = 1)
    public String getReference(CrawlEntry entry) {
        return entry.getReference();
    }

    public void setReference(CrawlEntry entry, String reference) {
        entry.setReference(reference);
    }

    @ProtoField(number = 2, required = true)
    //@ProtoDoc("@Field(index=Index.YES, analyze = Analyze.NO, store = Store.YES)")
    public String getProcessingStatus(CrawlEntry entry) {
        return entry.getProcessingStatus().name();
    }

    public void setProcessingStatus(CrawlEntry entry, String status) {
        entry.setProcessingStatus(ProcessingStatus.valueOf(status));
    }

    @ProtoField(number = 3)
    public String getMetaChecksum(CrawlEntry entry) {
        return entry.getMetaChecksum();
    }

    public void setMetaChecksum(CrawlEntry entry, String checksum) {
        entry.setMetaChecksum(checksum);
    }

    @ProtoField(number = 4)
    public String getContentChecksum(CrawlEntry entry) {
        return entry.getContentChecksum();
    }

    public void setContentChecksum(CrawlEntry entry, String checksum) {
        entry.setContentChecksum(checksum);
    }

    /**
     * Extracts additional metadata as a map.
     * This includes all fields not directly serialized with @ProtoField.
     * @param entry the CrawlEntry
     * @return the metadata map
     */
    @ProtoField(number = 5)
    public Map<String, String> getOtherProps(CrawlEntry entry) {

        try {
            var props = BeanUtils.describe(entry);
            Stream.of(
                    CrawlEntry.Fields.reference,
                    CrawlEntry.Fields.processingStatus,
                    CrawlEntry.Fields.metaChecksum,
                    CrawlEntry.Fields.contentChecksum).forEach(props::remove);
            return props;
        } catch (IllegalAccessException | InvocationTargetException
                | NoSuchMethodException e) {
            throw new CacheException("Could not serialize crawl entry.", e);
        }
    }

    /**
     * Sets the metadata on a CrawlEntry.
     * @param entry the CrawlEntry
     * @param metadata the metadata to set
     */
    public void setOtherProps(CrawlEntry entry, Map<String, String> metadata) {
        if (metadata == null) {
            return;
        }
        try {
            BeanUtils.populate(entry, metadata);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new CacheException("Could not deserialize crawl entry.", e);
        }
    }
}