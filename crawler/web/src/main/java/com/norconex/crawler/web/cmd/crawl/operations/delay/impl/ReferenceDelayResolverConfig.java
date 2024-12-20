/* Copyright 2016-2024 Norconex Inc.
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
package com.norconex.crawler.web.cmd.crawl.operations.delay.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link ReferenceDelayResolver}.
 * </p>
 * @since 2.5.0
 */
@Data
@Accessors(chain = true)
public class ReferenceDelayResolverConfig extends BaseDelayResolverConfig {

    private final List<DelayReferencePattern> delayReferencePatterns =
            new ArrayList<>();

    public List<DelayReferencePattern> getDelayReferencePatterns() {
        return Collections.unmodifiableList(delayReferencePatterns);
    }

    public ReferenceDelayResolverConfig setDelayReferencePatterns(
            List<DelayReferencePattern> delayReferencePatterns) {
        CollectionUtil.setAll(
                this.delayReferencePatterns, delayReferencePatterns);
        return this;
    }
}