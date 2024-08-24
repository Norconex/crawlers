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
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.doc.operations.filter.ReferenceFilter;

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
 * <h3>XML configuration usage:</h3>
 * Subclasses inherit this {@link XMLConfigurable} configuration:
 *
 * {@nx.xml.usage #referenceFilters
 * <referenceFilters>
 *   <!-- multiple "filter" tags allowed -->
 *   <filter class="(any reference filter class)">
 *      (Restrict usage of this fetcher to matching reference filters.
 *       Refer to the documentation for the ReferenceFilter implementation
 *       you are using here for usage details.)
 *   </filter>
 * </referenceFilters>
 * }
 *
 * <h4>Usage example:</h4>
 * <p>
 * This XML snippet is an example of filter that restricts the application of
 * this Fetcher to references ending with ".pdf".
 * </p>
 *
 * {@nx.xml.example
 * <referenceFilters>
 *   <filter class="GenericReferenceFilter" onMatch="exclude">
 *     <valueMatcher method="regex">.*\.pdf$</valueMatcher>
 *   </filter>
 * </referenceFilters>
 * }
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