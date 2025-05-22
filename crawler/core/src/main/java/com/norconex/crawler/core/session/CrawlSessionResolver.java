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

import java.time.Duration;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridTaskBuilder;
import com.norconex.grid.core.compute.TaskState;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class CrawlSessionResolver {

    private CrawlSessionResolver() {
    }

    static CrawlSession resolve(
            Grid grid, Duration sessionTimeout, String id) {
        var result = grid.getCompute().executeTask(GridTaskBuilder
                .create("resolveCrawlSession")
                .singleNode()
                .executor(g -> doResolve(g, sessionTimeout, id))
                .build());
        if (result.getState() == TaskState.FAILED) {
            throw new CrawlerException("Could not establish if there is a "
                    + "current crawl session. Error: " + result.getError());
        }
        return (CrawlSession) result.getResult();
    }

    private static CrawlSession doResolve(
            Grid grid, Duration sessionTimeout, String id) {
        //NOTE: invoked by coordinator
        var sessionStore = CrawlSessionManager.sessionStore(grid);
        var session = sessionStore.get(id);
        if (session == null) {
            LOG.info("No previous crawl session detected for crawler {}. "
                    + "Starting a new full crawl session.", id);
            session = new CrawlSession()
                    .setCrawlerId(id)
                    .setCrawlMode(CrawlMode.FULL)
                    .setResumeState(ResumeState.INITIAL);
        } else if (session.getCrawlState() == CrawlState.RUNNING) {
            if (System.currentTimeMillis()
                    - session.getLastUpdated() > sessionTimeout.toMillis()) {
                LOG.warn("A crawl session for crawler {} was "
                        + "detected but expired. Trying to resume it.", id);
                session.setResumeState(ResumeState.RESUMED);
            } else {
                LOG.info("Joining crawl session for crawler {}.", id);
            }
        } else if (session.getCrawlState() == CrawlState.PAUSED) {
            LOG.info("A previously paused crawl session was detected for. "
                    + "crawler {}. Resuming it.", id);
            session.setResumeState(ResumeState.RESUMED);
        } else if (session.getCrawlState() == CrawlState.COMPLETED) {
            LOG.info("A previously completed crawl session was detected for. "
                    + "crawler {}. Starting a new incremental crawl session.",
                    id);
            session.setCrawlMode(CrawlMode.INCREMENTAL);
        } else if (session.getCrawlState() == CrawlState.FAILED) {
            LOG.warn("A crawl session for crawler {} was detected but is "
                    + "marked as failed. Trying to resume it.", id);
            session.setResumeState(ResumeState.RESUMED);
        }

        //MAYBE: if we can detect here that the state is not valid for joining
        // throw exception instead.

        session.setCrawlState(CrawlState.RUNNING);
        session.setLastUpdated(System.currentTimeMillis());
        sessionStore.put(id, session);
        return session;
    }
}
