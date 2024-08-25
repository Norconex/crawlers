/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations.spoil;

import com.norconex.crawler.core.doc.CrawlDocState;

/**
 * <p>
 * Decides which strategy to adopt for a given reference with a bad state.
 * Those can
 * either be deleted (asking the committer to remove them from a target
 * repository),
 * they can be ignored (no action is taken), or graced (give is one chance
 * to recover on a subsequent run).
 * </p>
 * <p>
 * A "bad" state is any state but <code>NEW</code>, <code>MODIFIED</code>,
 * and <code>UNMODIFIED</code>. These statuses never have to be resolved.
 * A complete list of statuses can be obtained from {@link CrawlDocState}
 * or a subclass.
 * </p>
 * <p>
 * In addition to "good" states, it is possible for some states to be temporary
 * and/or to be specific to some collectors, and to never be passed
 * to this class.
 * </p>
 */
public interface SpoiledReferenceStrategizer {

    /**
     * Establish which spoiled reference strategy to adopt.
     * @param reference a document reference
     * @param state the reference crawl state to evaluate
     * @return a spoiled reference strategy
     */
    SpoiledReferenceStrategy resolveSpoiledReferenceStrategy(
            String reference, CrawlDocState state
    );
}
