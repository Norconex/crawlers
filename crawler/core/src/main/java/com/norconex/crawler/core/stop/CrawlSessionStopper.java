/* Copyright 2021-2023 Norconex Inc.
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
package com.norconex.crawler.core.stop;

import com.norconex.crawler.core.session.CrawlSession;

/**
 * <p>
 * Responsible for shutting down a crawl session upon explicit invocation
 * of {@link #fireStopRequest(CrawlSession)} or when specific conditions are
 * met.
 * See concrete implementation for what those conditions could be.
 * </p>
 * <p>
 * Stop requests are typically triggered from another JVM (see concrete
 * implementation details) and possibly from a different server (in a cluster).
 * </p>
 */
public interface CrawlSessionStopper {

    /**
     * Setup and/or start the stopper, which can be terminated
     * by invoking stop in the same or different JVM (see concrete
     * implementation for details).
     * @param crawlSession the crawl session
     * @throws CrawlSessionStopperException could not listen to stop requests.
     */
    void listenForStopRequest(CrawlSession crawlSession)
            throws CrawlSessionStopperException;
    /**
     * Destroys resources allocated with this stopper.
     * Called at the end of a crawl session execution.
     * @throws CrawlSessionStopperException could not destroy
     *     crawl session stopper.
     */
    void destroy() throws CrawlSessionStopperException;

    /**
     * Stops a currently running crawl session.
     * @param crawlSession crawl session
     * @return <code>true</code> if the crawl session was running and
     *     successfully stopped or <code>false</code> if the crawl session was
     *     not running.
     * @throws CrawlSessionStopperException could not stop running crawl session.
     */
    boolean fireStopRequest(CrawlSession crawlSession)
            throws CrawlSessionStopperException;
}
