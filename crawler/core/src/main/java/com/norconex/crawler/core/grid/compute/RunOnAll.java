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

import org.apache.commons.lang3.mutable.MutableObject;

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
class RunOnAll {

    //TODO introduce heartbeat/expiry

    // Unlike others, a cache is created for each job, where each record
    // is a node and its status.
    //TODO then destroyed when done?

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
                var ctx = new ComputeOnAllContext(grid, jobName);
                //TODO within a transaction?
                var clusterState = ctx.getComputeState();
                var thisState = ctx.getThisState();
                if (!RunUtil.canRun(jobName, clusterState, runOnce)
                        || !RunUtil.canRun(jobName, thisState, runOnce)) {
                    return;
                }
                ctx.setThisState(ComputeState.RUNNING);
                var t = runIt(ctx, callable);
                watchForCompletion(ctx);
                future.complete(t);
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                executor.shutdown();
            }
        });
        return future;
    }

    private <T> T runIt(ComputeOnAllContext ctx, Callable<T> callable) {
        LOG.info("Running job on all nodes{}: {}",
                runOnce ? " once for this crawl session" : "",
                ctx.getJobName());
        try {
            var t = callable.call();
            ctx.setThisState(ComputeState.DONE);
            return t;
        } catch (Exception e) {
            LOG.error("Job {} failed on this node.", ctx.jobName, e);
            ctx.setThisState(ComputeState.FAILED);
            return null;
        }
    }

    private void watchForCompletion(ComputeOnAllContext ctx) {
        LOG.info("Waiting for job {} to complete on other nodes.", ctx.jobName);
        //TODO have a timeout where we force exit?
        var done = false;
        while (!done) {
            // The clean command can make it that there is no status
            // for a while, but the "runOnceCache" is the last one
            // cleared (is it?) so the exiting from here is not premature.
            if (ctx.getComputeState().hasRan()) {
                done = true;
            } else {
                Sleeper.sleepSeconds(1);
            }
        }
    }

    /*
       CACHE: run-on-all-[jobName]-[sessionId]
         [nodeIndex] -> ComputState
         [nodeIndex] -> ComputState
         [nodeIndex] -> ComputState
     */
    @Getter
    private static class ComputeOnAllContext {
        //        private static final String RUN_ON_ALL_CACHE = "run-on-all";
        //        private static final String RUN_ON_ALL_ONCE_CACHE = "run-on-all-once";

        private final String jobName;
        private final GridMap<ComputeState> allJobStates;
        private final String nodeIndex;

        public ComputeOnAllContext(Grid grid, String jobName) {
            this.jobName = jobName;
            allJobStates = grid.storage().getMap(
                    jobName + "-" + Grid.SESSION_ID, ComputeState.class);
            nodeIndex = Grid.NODE_ID;
        }

        void setThisState(@NonNull ComputeState state) {
            allJobStates.put(nodeIndex, state);
        }

        ComputeState getThisState() {
            return ofNullable(allJobStates.get(nodeIndex))
                    .orElse(ComputeState.IDLE);
        }

        ComputeState getComputeState() {
            var highestState = new MutableObject<>(ComputeState.IDLE);
            LOG.info("RunOnAll \"{}\" compute state count: {}",
                    allJobStates.getName(), allJobStates.size());
            allJobStates.forEach((k, v) -> {
                LOG.info("RunOnAll \"{}\" copute state: {} -> {}",
                        allJobStates.getName(), k, v);
                if (v.ordinal() > highestState.getValue().ordinal()) {
                    highestState.setValue(v);
                }
                return v != ComputeState.FAILED;
            });
            return highestState.getValue();
        }

        //        List<ComputeState> getClusterStates() {
        //            var states = new ArrayList<ComputeState>();
        //            cache.forEach((k, v) -> {
        //                states.add(v);
        //                return true;
        //            });
        //            return states;
        //        }
    }
}
