/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc;

/**
 * The different stages of a document processing life-cycle:
 * <code>NONE -&gt; QUEUED -&gt; UNRESOLVED -&gt; RESOLVED</code>
 */
public enum CrawlDocStage {
    /**
     * Nothing was done with the document yet. Could be rejected
     * before being queued, which would leave no trace in grid storage.
     * This is the default stage.
     */
    NONE,
    /**
     * Queued in grid storage for processing. Queuing a reference
     * automatically set the state to queued.
     */
    QUEUED,
    /**
     * Processing started but did not yet finish or failed.
     * Polling a document context from the store will automatically
     * make it part of the processed ones, but "unresolved" until
     * fully processed.
     * Documents identified as unresolved when a crawl session is over
     * are considered to be the result of an unrecoverable error.
     */
    UNRESOLVED,
    /**
     * Processing completed without issues for this document.
     * A document is automatically set to "resolved" when it is finalized.
     */
    RESOLVED;

    public boolean is(CrawlDocStage processingStage) {
        return processingStage != null && processingStage == this;
    }

}
