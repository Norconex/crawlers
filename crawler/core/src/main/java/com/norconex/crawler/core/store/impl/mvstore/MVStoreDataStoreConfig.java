/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.core.store.impl.mvstore;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
* <p>
* MVStore configuration parameters.  For advanced use only.
* Differences from MVStore defaults:
* All data size values are expected to be set in bytes.
* Light compression is enabled by default (compress = 1)
* </p>
* <p>
* For more info:
* </p>
* <ul>
*   <li><a href="http://www.h2database.com/html/mvstore.html">
*       MVStore documentation</a></li>
*   <li><a href="https://javadoc.io/doc/com.h2database/h2-mvstore/latest/">
*       Javadoc</a></li>
* </ul>
* @since 1.10.0
* @author Pascal Essiembre
*/
@Data
@FieldNameConstants
public class MVStoreDataStoreConfig {

    /**
     * The max memory page size in bytes before splitting it.
     * Defaults to 4KB for memory, and  16KB for disk.
     * @param pageSplitSize split size
     * @return page size
     */
    @SuppressWarnings("javadoc")
    private Long pageSplitSize;

    /**
     * <p>The level of compression when storing data. Supported values:</p>
     * <ul>
     *   <li>0: No compression</li>
     *   <li>1: Low compression (default)</li>
     *   <li>2: High compression</li>
     * </ul>
     * @param compress level of compression
     * @return level of compression
     */
    @SuppressWarnings("javadoc")
    private Integer compress = 1;

    /**
     * The maximum number of concurrent operations when reading from
     * the store cache. Default is 16.
     * @param cacheConcurrency maximum number of concurrent operations
     * @return maximum number of concurrent operations
     */
    @SuppressWarnings("javadoc")
    private Integer cacheConcurrency = 16;

    /**
     * The read cache size in bytes. Default is 16MB.
     * @param cacheSize read cache size
     * @return read cache size
     */
    @SuppressWarnings("javadoc")
    private Long cacheSize;

    /**
     * The auto-compact target fill rate, in percentage. Default is 90%.
     * @param autoCompactFillRate auto compact fill rate
     * @return auto compact fill rate
     */
    @SuppressWarnings("javadoc")
    private Integer autoCompactFillRate;

    /**
     * The size of the write buffer. Defaults to 1024KB.
     * @param autoCommitBufferSize size of the write buffer
     * @return size of the write buffer
     */
    @SuppressWarnings("javadoc")
    private Long autoCommitBufferSize;

    /**
     * The maximum delay in milliseconds to auto-commit changes. Defaults
     * to 1000ms (1 second).
     * @param autoCommitDelay maximum delay to auto-commit changes
     * @return maximum delay to auto-commit changes
     */
    @SuppressWarnings("javadoc")
    private Long autoCommitDelay;

    /**
     * Stores data in memory and does not persist any information
     * between each crawling sessions (breaking from the crawler
     * normal behavior).
     * <b>Not recommended for regular use.</b>
     * Useful for testing and troubleshooting, or if you know what your doing.
     * @param ephemeral whether to persist store data or keep it all in memory
     * @return <code>true</code> if only using memory (data is not persisted)
     */
    @SuppressWarnings("javadoc")
    private boolean ephemeral;
}