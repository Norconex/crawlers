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
package com.norconex.crawler.core.grid.compute;

import static java.util.Optional.ofNullable;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.storage.GridMap;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// do not wait for all to finish before returning
@Slf4j
@RequiredArgsConstructor
class RunSynchronized {
    private final Grid grid;

    public <T> Future<T> execute(String jobName, Callable<T> runnable)
            throws GridException {
        //TODO this:
        LOG.warn("TODO: add hearbeat to task execution on grid.");
        var executor = Executors.newFixedThreadPool(1);
        var future = new CompletableFuture<T>();
        executor.submit((Runnable) () -> {
            try {
                var runCtx = new ComputeSyncContext(grid, jobName);
                while (!isMyTurn(runCtx)) {
                    Sleeper.sleepSeconds(1);
                }
                var t = runIt(runCtx, runnable);
                future.complete(t);
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                executor.shutdown();
            }
        });
        return future;
    }

    private boolean isMyTurn(ComputeSyncContext ctx) {
        return grid.transactions().runInTransaction(() -> {
            if (!ctx.getState().isRunning()) {
                return ctx.setState(ComputeState.RUNNING);
            }
            return false;
        });
    }

    private <T> T runIt(ComputeSyncContext ctx, Callable<T> callable) {
        LOG.info("Running job {}.", ctx.getJobName());
        try {
            var t = callable.call();
            ctx.setState(ComputeState.DONE);
            return t;
        } catch (Exception e) {
            LOG.error("Job {} failed.", ctx.jobName, e);
            ctx.setState(ComputeState.FAILED);
            return null;
        }
    }

    /*
       CACHE: [run-on-one]
         [jobName]-[sessionId} -> ComputState
     */
    @Getter
    private static class ComputeSyncContext {
        //NOTE: We don't care to have a cache for once vs not-once as
        // for the same job name, it will always be the same.
        private static final String RUN_SYNC_CACHE = "run-sync";

        private final String jobName;
        private final String sessionJobName;
        private final GridMap<ComputeState> cache;

        public ComputeSyncContext(Grid grid, String jobName) {
            this.jobName = jobName;
            sessionJobName = jobName + "-" + Grid.SESSION_ID;
            cache = grid.storage().getMap(RUN_SYNC_CACHE, ComputeState.class);
        }

        ComputeState getState() {
            return ofNullable(cache.get(sessionJobName))
                    .orElse(ComputeState.IDLE);
        }

        boolean setState(@NonNull ComputeState state) {
            return cache.put(sessionJobName, state);
        }
    }
}
