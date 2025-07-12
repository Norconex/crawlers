/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.session;

/**
 * Describes how a job was launched (new launch or resuming a non
 * completed/failed one).
 */
public enum LaunchMode {
    /**
     * The crawler started from the beginning (first-time or restarted after
     * success, regardless of crawl mode -- incremental or full).
     */
    NEW,
    /**
     * The crawler resumed from a prior incomplete execution (e.g., paused
     * or failed).
     */
    RESUMED
}
