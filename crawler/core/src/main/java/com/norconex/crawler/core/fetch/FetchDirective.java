/* Copyright 2023-2024 Norconex Inc.
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

/**
 * Whether to perform metadata extraction on its own before (or instead)
 * performing document extraction as a whole (including metadata).
 */
public enum FetchDirective {
    /**
     * A directive to execute a fetch request separate from document fetch.
     */
    METADATA,
    /**
     * A directive to execute a fetch request for the entire
     * document, including metadata.
     */
    DOCUMENT;

    public boolean is(FetchDirective fetchDirective) {
        return equals(fetchDirective);
    }
}