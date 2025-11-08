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
package com.norconex.crawler.core.cluster.impl.infinispan;

/**
 * Interface for controlling crawler stop requests. Implementations
 * monitor for stop signals and trigger graceful shutdown when detected.
 * <p>
 * Implementations may use different mechanisms for stop signaling:
 * </p>
 * <ul>
 *   <li>Cache-based (for clustered mode)</li>
 *   <li>File-based (for standalone mode)</li>
 * </ul>
 */
public interface CrawlerStopController {

    /**
     * Starts monitoring for stop signals. Should be called during
     * crawler initialization.
     */
    void start();

    /**
     * Stops monitoring and performs cleanup. Should be called during
     * crawler shutdown.
     */
    void stop();
}
