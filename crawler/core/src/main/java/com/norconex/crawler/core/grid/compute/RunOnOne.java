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

@Slf4j
@RequiredArgsConstructor
final class RunOnOne {

    //TODO introduce heartbeat

    private final Grid grid;
    private final boolean runOnce;

    public <T> Future<T> execute(String jobName, Callable<T> callable)
            throws GridException {
        //TODO this:
        LOG.warn("TODO: add hearbeat to task execution on grid.");
        var executor = Executors.newFixedThreadPool(1);
        var future = new CompletableFuture<T>();
        executor.submit((Runnable) () -> {
            try {
                var runCtx = new ComputeOnOneContext(grid, jobName);
                T value = null;
                if (isChosenOne(runCtx)) {
                    value = runIt(runCtx, callable);
                } else {
                    watchForCompletion(runCtx);
                }
                future.complete(value);
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                executor.shutdown();
            }
        });
        return future;
    }

    private boolean isChosenOne(ComputeOnOneContext ctx) {
        return grid.transactions().runInTransaction(() -> {
            if (RunUtil.canRun(
                    ctx.getJobName(), ctx.getState(), runOnce)) {
                return ctx.setState(ComputeState.RUNNING);
            }
            return false;
        });
    }

    private <T> T runIt(ComputeOnOneContext ctx, Callable<T> callable) {
        LOG.info("Running job on this instance only{}: {}",
                runOnce ? " once for this crawl session" : "",
                ctx.getJobName());
        try {
            var value = callable.call();
            ctx.setState(ComputeState.DONE);
            return value;
        } catch (Exception e) {
            LOG.error("Job {} failed.", ctx.jobName, e);
            ctx.setState(ComputeState.FAILED);
            return null;
        }
    }

    private void watchForCompletion(ComputeOnOneContext ctx) {
        LOG.info("Job run by another node: {}", ctx.getJobName());
        //TODO have a timeout where we force exit?
        var done = false;
        while (!done) {
            // The clean command can make it that there is no status
            // for a while, but the "runOnceCache" is the last one
            // cleared (is it?) so the exiting from here is not premature.
            if (ctx.getState().hasRan()) {
                done = true;
            } else {
                Sleeper.sleepSeconds(1);
            }
        }
    }

    /*
       CACHE: [run-on-one]
         [jobName]-[sessionId} -> ComputState
     */
    @Getter
    private static class ComputeOnOneContext {
        //NOTE: We don't care to have a cache for once vs not-once as
        // for the same job name, it will always be the same.
        private static final String RUN_ON_ONE_CACHE = "run-on-one";

        private final String jobName;
        private final String sessionJobName;
        private final GridMap<ComputeState> cache;

        public ComputeOnOneContext(Grid grid, String jobName) {
            this.jobName = jobName;
            sessionJobName = jobName + "-" + Grid.SESSION_ID;
            cache = grid.storage().getMap(RUN_ON_ONE_CACHE, ComputeState.class);
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
