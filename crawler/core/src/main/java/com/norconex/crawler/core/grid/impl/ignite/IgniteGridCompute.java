/* Copyright 2024 Norconex Inc.
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

import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.lang.IgniteFuture;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;
import com.norconex.crawler.core.grid.GridTxOptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class IgniteGridCompute implements GridCompute {

    private final IgniteGridInstance igniteGridInstance;

    @Override
    public Future<?> runOnce(String jobName, Runnable runnable) {
        var lock = igniteGridInstance.get().reentrantLock(
                jobName, true, true, true);
        var runOnceCache =
                igniteGridInstance.get().<String, String>getOrCreateCache(
                        IgniteGridKeys.RUN_ONCE_CACHE);
        var chosenOne = false;
        try {
            lock.lock();
            chosenOne = runOnceCache.putIfAbsent(jobName, "running");
        } finally {
            lock.unlock();
        }

        var executor = Executors.newFixedThreadPool(1);
        try {
            if (chosenOne) {
                LOG.info("Running job once: {}", jobName);
                return executor.submit(() -> {
                    try {
                        runnable.run();
                        runOnceCache.put(jobName, "done");
                    } finally {
                        runOnceCache.put(jobName, "failed");
                    }
                });
            }
            LOG.info("Job run by another node: {}", jobName);
            return executor.submit(() -> {
                //TODO have a timeout?
                var done = false;
                while (!done) {
                    var status = runOnceCache.get(jobName);
                    if (StringUtils.isBlank(status)
                            || StringUtils.equalsAny(status, "done",
                                    "failed")) {
                        done = true;
                    } else {
                        Sleeper.sleepSeconds(1);
                    }
                }
            });
        } finally {
            executor.shutdown();
        }
    }

    @Override
    public void runTask(
            Class<? extends GridTask> taskClass, String arg,
            GridTxOptions opts)
            throws GridException {

        var className = taskClass.getName();
        var gridRunnable = gridRunnable(className, arg, opts);

        if (opts.isAtomic()) {
            gridRunnable = withAtomic(gridRunnable);
        }

        if (opts.isLock()) {
            gridRunnable = withLock(opts.getName(), gridRunnable);
        }

        gridRunnable.run();
    }

    private Runnable gridRunnable(String className, String arg,
            GridTxOptions opts) {
        return () -> {
            IgniteFuture<Void> future;

            if (opts.isSingleton()) {
                future = igniteGridInstance.get().compute()
                        .runAsync(() -> IgniteGridServerTaskRunner
                                .execute(className, arg));//  finalTask::run);
                //            future = ignite.services().deployClusterSingletonAsync(name,
                //                    service);
            } else {
                //NOTE: deployMultipleAsync is independent on each nodes so
                // the total count is multiplied by the number of app instances.
                // to prevent this, we wrap the multi call in a singleton call.
                // This ensure only one request for running multiple requests
                // is made

                // TODO take values from ignite config?
                //            ignite.services().clusterGroup().
                future = igniteGridInstance.get().compute()
                        .broadcastAsync(() -> IgniteGridServerTaskRunner
                                .execute(className, arg)); //finalTask::run);
                //
                //            future = ignite.services().deployMultipleAsync(name,
                //                    service, 0, 1);
            }

            if (opts.isBlock()) {
                future.get(); // blocks until completion
            }
        };
    }

    private Runnable withLock(String name, Runnable runnableWrapper) {
        return () -> {
            var lock =
                    igniteGridInstance.get().reentrantLock(name + "-lock", true,
                            true, true);
            try {
                lock.lock();
                runnableWrapper.run();
            } finally {
                lock.unlock();
            }
        };
    }

    private Runnable withAtomic(Runnable runnableWrapper) {
        return () -> {
            try (var tx = igniteGridInstance.get().transactions().txStart()) {
                runnableWrapper.run();
                tx.commit();
            }
        };
    }

}
