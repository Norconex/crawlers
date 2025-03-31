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
package com.norconex.grid.local;

import java.time.Duration;

import com.norconex.commons.lang.unit.DataUnit;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link LocalGridConnector}.
 * </p>
 * <p>
 * For the most part, the configuration options a directly derived from MVStore
 * options. Changing default values is for advanced use only. Differences from
 * MVStore defaults:
 * </p>
 * <ul>
 *   <li>All data size values are expected to be set in bytes.</li>
 *   <li>Light compression is enabled by default (compress = 1)</li>
 * </ul>
 * <p>
 * For more info:
 * </p>
 * <ul>
 *   <li><a href="http://www.h2database.com/html/mvstore.html">
 *       MVStore documentation</a></li>
 *   <li><a href="https://javadoc.io/doc/com.h2database/h2-mvstore/latest/">
 *       Javadoc</a></li>
 * </ul>
 */
@Data
@Accessors(chain = true)
public class LocalGridConnectorConfig {

    /**
     * The max memory page size in bytes before splitting it.
     * Defaults to 4KB for memory, and  16KB for disk.
     */
    private Long pageSplitSize;

    /**
     * <p>The level of compression when storing data. Supported values:</p>
     * <ul>
     *   <li>0: No compression</li>
     *   <li>1: Low compression (default)</li>
     *   <li>2: High compression</li>
     * </ul>
     */
    private Integer compress = 1;

    /**
     * The maximum number of concurrent operations when reading from
     * the store cache. Default is 16.
     */
    private Integer cacheConcurrency = 16;

    /**
     * The read cache size in bytes. Default is 16MB.
     */
    private Long cacheSize = DataUnit.MB.toBytes(16).longValue();

    /**
     * The auto-compact target fill rate, in percentage. Default is 90%.
     */
    private Integer autoCompactFillRate = 90;

    /**
     * The size of the write buffer. Defaults to 1024KB.
     */
    private Long autoCommitBufferSize = DataUnit.KB.toBytes(1024).longValue();

    /**
     * The maximum delay in milliseconds to auto-commit changes. Defaults
     * to 1000ms (1 second).
     */
    private Long autoCommitDelay = Duration.ofSeconds(1).toMillis();

    /**
     * Stores data in memory and does not persist any information
     * between each crawling sessions (breaking from the crawler
     * normal behavior).
     * <b>Not recommended for regular use.</b>
     * Useful for testing and troubleshooting, or if you know what your doing.
     */
    private boolean ephemeral;

}
