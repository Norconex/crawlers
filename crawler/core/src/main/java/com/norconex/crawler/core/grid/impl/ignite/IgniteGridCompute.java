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
package com.norconex.crawler.core.grid.impl.ignite;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.compute.BroadcastJobTarget;
import org.apache.ignite.compute.JobDescriptor;
import org.apache.ignite.compute.JobTarget;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class IgniteGridCompute implements GridCompute {

    private final IgniteGrid igniteGrid;

    @Override
    public <T> Future<T> runOnOneOnce(String jobName, Callable<T> callable)
            throws GridException {

        var sessionJobName =
                jobName + "-" + IgniteGridConnector.CRAWL_SESSION_ID;
        var runOnceCache = igniteGrid.storage().getCache(
                IgniteGridKeys.RUN_ONCE_CACHE, String.class);

        boolean chosenOne = igniteGrid.getIgniteApi().transactions()
                .runInTransaction(tx -> {
                    var existingStatus = runOnceCache.get(sessionJobName);
                    if (existingStatus == null) {
                        return runOnceCache.put(sessionJobName, "running");
                    }
                    if (hasJobRan(existingStatus)) {
                        LOG.info("Job \"{}\" already ran in this crawl session "
                                + "with status: \"{}\".",
                                jobName, existingStatus);
                    }
                    return false;
                });

        var executor = Executors.newFixedThreadPool(1);
        try {
            if (chosenOne) {
                LOG.info("Running job once: {}", jobName);
                return executor.submit(() -> {
                    try {
                        var value = callable.call();
                        runOnceCache.put(sessionJobName, "done");
                        return value;
                    } catch (Exception e) {
                        runOnceCache.put(sessionJobName, "failed");
                        return null;
                    }
                });
            }
            LOG.info("Job run by another node: {}", jobName);
            return executor.submit(() -> {
                //TODO have a timeout where we force exit?
                var done = false;
                while (!done) {
                    // The clean command can make it that there is no status
                    // for a while, but the "runOnceCache" is the last one
                    // cleared so the exiting from here is not premature.
                    var status = runOnceCache.get(sessionJobName);
                    if (StringUtils.isBlank(status)
                            || hasJobRan(status)) {
                        done = true;
                    } else {
                        Sleeper.sleepSeconds(1);
                    }
                }
                return null;
            });
        } finally {
            executor.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Future<Collection<? extends T>> runOnAll(
            Class<? extends GridTask<T>> taskClass, String arg)
            throws GridException {

        var api = igniteGrid.getIgniteApi();
        return api.transactions()
                .runInTransactionAsync(tx -> api.compute().executeAsync(
                        BroadcastJobTarget.nodes(api.clusterNodes()),
                        JobDescriptor
                                .builder(IgniteGridTaskAdapter.class)
                                .build(),
                        IgniteGridTaskAdapter.toComputeArg(taskClass, arg)))
                .thenApply(collection -> collection.stream()
                        .map(obj -> (T) obj)
                        .toList());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Future<T> runOnOne(
            Class<? extends GridTask<T>> taskClass, String arg)
            throws GridException {

        var api = igniteGrid.getIgniteApi();
        return (Future<T>) api.transactions()
                .runInTransactionAsync(tx -> api.compute().executeAsync(
                        JobTarget.anyNode(api.clusterNodes()),
                        JobDescriptor
                                .builder(IgniteGridTaskAdapter.class)
                                .build(),
                        IgniteGridTaskAdapter.toComputeArg(taskClass, arg)));
    }

    private boolean hasJobRan(String status) {
        return StringUtils.equalsAny(status, "done", "failed");
    }
}
