/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.cluster.impl.mvstore;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Configuration for the MVStore-based cluster connector.
 * All settings have sensible defaults and can usually be left unchanged.
 */
@Data
@Accessors(chain = true)
public class MVStoreClusterConnectorConfig {

    /**
     * The page split size in bytes. Default is 4096.
     * Higher values may improve sequential read performance for large
     * values but can waste space for smaller entries.
     */
    private int pageSplitSize = 4096;

    /**
     * The compression level (0=off, 1=fast/LZF, 2=high/Deflate).
     * Default is 0 (no compression).
     */
    private int compress;

    /**
     * The cache size in MB. Default is 16.
     */
    private int cacheSize = 16;

    /**
     * The auto-compact fill rate (0-100). A lower value triggers
     * compaction more aggressively. Default is 40.
     */
    private int autoCompactFillRate = 40;

    /**
     * The auto-commit buffer size in KB. When the buffer exceeds this
     * size, changes are automatically committed. Default is 1024 (1 MB).
     */
    private int autoCommitBufferSize = 1024;

    /**
     * The auto-commit delay in milliseconds. Default is 1000 (1 second).
     * Set to 0 to disable auto-commit by time (data is still committed
     * by buffer size).
     */
    private int autoCommitDelay = 1000;
}
