/* Copyright 2015-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations.spoil.impl;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategy;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link GenericSpoiledReferenceStrategizer}.
 * </p>
 */
@Data
@Accessors(chain = true)
public class GenericSpoiledReferenceStrategizerConfig {

    public static final SpoiledReferenceStrategy DEFAULT_FALLBACK_STRATEGY =
            SpoiledReferenceStrategy.DELETE;

    private final Map<CrawlDocStatus, SpoiledReferenceStrategy> mappings =
            new HashMap<>();
    private SpoiledReferenceStrategy fallbackStrategy =
            DEFAULT_FALLBACK_STRATEGY;

    public GenericSpoiledReferenceStrategizerConfig() {
        // store default mappings
        mappings.put(CrawlDocStatus.NOT_FOUND,
                SpoiledReferenceStrategy.DELETE);
        mappings.put(
                CrawlDocStatus.BAD_STATUS,
                SpoiledReferenceStrategy.GRACE_ONCE);
        mappings.put(CrawlDocStatus.ERROR,
                SpoiledReferenceStrategy.GRACE_ONCE);
    }

    @JsonIgnore
    public GenericSpoiledReferenceStrategizerConfig setMapping(
            CrawlDocStatus state, SpoiledReferenceStrategy strategy) {
        mappings.put(state, strategy);
        return this;
    }

    public GenericSpoiledReferenceStrategizerConfig setMappings(
            Map<CrawlDocStatus, SpoiledReferenceStrategy> mappings) {
        this.mappings.clear();
        this.mappings.putAll(mappings);
        return this;
    }
}
