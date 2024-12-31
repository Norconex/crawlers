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

import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Lightweight version of both default Ignite {@link DataStorageConfiguration}
 * and default {@link DataRegionConfiguration}.
 */
@Data
@Accessors(chain = true)
public class LightIgniteStorageConfig {

    private String name = DataStorageConfiguration.DFLT_DATA_REG_DEFAULT_NAME;
    private long initialSize = Math.min(
            DataStorageConfiguration.DFLT_DATA_REGION_MAX_SIZE,
            DataStorageConfiguration.DFLT_DATA_REGION_INITIAL_SIZE);
    private long maxSize = DataStorageConfiguration.DFLT_DATA_REGION_MAX_SIZE;
    /**
     * Set to <code>true</code> to turn off persistence. Setting this to
     * <code>false</code> disables the ability to do incremental crawls
     * (each crawl will be a "fresh" one). Persistence is enabled by default.
     */
    private boolean persistenceDisabled;

}
