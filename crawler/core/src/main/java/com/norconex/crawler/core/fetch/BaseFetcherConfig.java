/* Copyright 2022-2024 Norconex Inc.
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
package com.norconex.crawler.core.fetch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.crawler.core.cmd.crawl.operations.filter.ReferenceFilter;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Base class implementing the {@link AbstractFetcher#accept(FetchRequest)}
 * method using reference filters to determine if this fetcher will accept to
 * fetch a document, in addition to whatever logic implementing classes may
 * provide by optionally overriding
 * {@link AbstractFetcher#acceptRequest(FetchRequest)}
 * (which otherwise always return <code>true</code>).
 * It also offers methods to overwrite in order to react to crawler
 * startup and shutdown events.
 * </p>
 */
@Data
@Accessors(chain = true)
public class BaseFetcherConfig {

    private final List<ReferenceFilter> referenceFilters = new ArrayList<>();

    /**
     * Gets reference filters
     * @return reference filters
     */
    public List<ReferenceFilter> getReferenceFilters() {
        return Collections.unmodifiableList(referenceFilters);
    }

    /**
     * Sets reference filters.
     * @param referenceFilters the referenceFilters to set
     * @return this instance
     */
    public BaseFetcherConfig setReferenceFilters(
            List<ReferenceFilter> referenceFilters) {
        CollectionUtil.setAll(this.referenceFilters, referenceFilters);
        CollectionUtil.removeNulls(this.referenceFilters);
        return this;
    }
}