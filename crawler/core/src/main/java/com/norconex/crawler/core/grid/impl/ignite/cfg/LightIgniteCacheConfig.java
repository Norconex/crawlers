/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.grid.impl.ignite.cfg;

import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheRebalanceMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.PartitionLossPolicy;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DiskPageCompression;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Lightweight version of Ignite {@link CacheConfiguration}.
 */
@Data
@Accessors(chain = true)
public class LightIgniteCacheConfig {
    private CacheAtomicityMode atomicityMode =
            CacheConfiguration.DFLT_CACHE_ATOMICITY_MODE;
    private int backups = CacheConfiguration.DFLT_BACKUPS;
    private CacheMode cacheMode = CacheConfiguration.DFLT_CACHE_MODE;
    /** Whether to disable copy on read. */
    private boolean copyOnReadDisabled = !CacheConfiguration.DFLT_COPY_ON_READ;
    private DiskPageCompression diskPageCompression =
            CacheConfiguration.DFLT_DISK_PAGE_COMPRESSION;
    private Integer diskPageCompressionLevel;
    /** Whether to disable eager TTL. */
    private boolean eagerTtlDisabled = !CacheConfiguration.DFLT_EAGER_TTL;
    private boolean encryptionEnabled;
    private boolean eventsDisabled = CacheConfiguration.DFLT_EVENTS_DISABLED;
    private String groupName;
    private boolean invalidate = CacheConfiguration.DFLT_INVALIDATE;
    private boolean loadPreviousValue = CacheConfiguration.DFLT_LOAD_PREV_VAL;
    private boolean managementEnabled;
    private int maxConcurrentAsyncOperations =
            CacheConfiguration.DFLT_MAX_CONCURRENT_ASYNC_OPS;
    private int maxQueryIteratorsCount =
            CacheConfiguration.DFLT_MAX_QUERY_ITERATOR_CNT;
    private String name;
    private boolean onheapCacheEnabled;
    private PartitionLossPolicy partitionLossPolicy =
            CacheConfiguration.DFLT_PARTITION_LOSS_POLICY;
    private int queryDetailMetricsSize =
            CacheConfiguration.DFLT_QRY_DETAIL_METRICS_SIZE;
    private int queryParallelism = CacheConfiguration.DFLT_QUERY_PARALLELISM;
    /** Whether to disable reading from backup. */
    private boolean readFromBackupDisabled =
            !CacheConfiguration.DFLT_READ_FROM_BACKUP;
    private boolean readThrough;
    private CacheRebalanceMode rebalanceMode =
            CacheConfiguration.DFLT_REBALANCE_MODE;
    private int rebalanceOrder;
    private boolean statisticsEnabled;
    private boolean storeByValue;
    private int storeConcurrentLoadAllThreshold =
            CacheConfiguration.DFLT_CONCURRENT_LOAD_ALL_THRESHOLD;
    private boolean storeKeepBinary;
    private int writeBehindBatchSize =
            CacheConfiguration.DFLT_WRITE_BEHIND_BATCH_SIZE;
    /** Whether to disable write-behind coalescing. */
    private boolean writeBehindCoalescingDisabled =
            !CacheConfiguration.DFLT_WRITE_BEHIND_COALESCING;
    private boolean writeBehindEnabled =
            CacheConfiguration.DFLT_WRITE_BEHIND_ENABLED;
    private long writeBehindFlushFrequency =
            CacheConfiguration.DFLT_WRITE_BEHIND_FLUSH_FREQUENCY;
    private int writeBehindFlushSize =
            CacheConfiguration.DFLT_WRITE_BEHIND_FLUSH_SIZE;
    private int writeBehindFlushThreadCount =
            CacheConfiguration.DFLT_WRITE_FROM_BEHIND_FLUSH_THREAD_CNT;
    private CacheWriteSynchronizationMode writeSynchronizationMode;
    private boolean writeThrough;
}
