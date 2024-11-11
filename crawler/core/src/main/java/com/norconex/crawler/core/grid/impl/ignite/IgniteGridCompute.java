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

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;
import com.norconex.crawler.core.grid.GridTxOptions;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class IgniteGridCompute implements GridCompute {

    private final IgniteGrid igniteGrid;

    @Override
    public <T> Future<T> runLocalOnce(String jobName, Callable<T> callable)
            throws GridException {

        var lock = igniteGrid.getIgnite().reentrantLock(
                jobName, true, true, true);
        var runOnceCache = igniteGrid.storage().getCache(
                IgniteGridKeys.RUN_ONCE_CACHE, String.class);

        var chosenOne = false;
        try {
            lock.lock();
            var existingStatus = runOnceCache.get(jobName);
            if (existingStatus != null) {
                chosenOne = false;
                if (hasJobRan(existingStatus)) {
                    LOG.info("Job \"{}\" already ran with status: \"{}\".",
                            jobName, existingStatus);
                    return CompletableFuture.completedFuture(null);
                }
            } else {
                chosenOne = runOnceCache.put(jobName, "running");
            }

            //            chosenOne = ((IgniteGridCache<String>) runOnceCache).getCache()
            //                    .putIfAbsent(jobName, "running");

            System.err.println("XXX Am I the chosen one? " + chosenOne);
            System.err.println("XXX Job name: " + jobName);
            System.err.println(
                    "XXX Existing Value: " + runOnceCache.get(jobName));
        } finally {
            lock.unlock();
        }

        var executor = Executors.newFixedThreadPool(1);
        try {
            if (chosenOne) {
                LOG.info("Running job once: {}", jobName);
                return executor.submit(() -> {
                    try {
                        var value = callable.call();
                        runOnceCache.put(jobName, "done");
                        return value;
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
    //
    //    @Override
    //    public <T> Future<T> runLocalAtomic(Callable<T> callable)
    //            throws GridException {
    //        return CompletableFuture.supplyAsync(() -> {
    //            try (var tx = igniteGrid.getIgnite().transactions().txStart(
    //                    TransactionConcurrency.PESSIMISTIC,
    //                    TransactionIsolation.REPEATABLE_READ)) {
    //                var result = callable.call();
    //                System.err.println("   XXX result: " + result);
    //                tx.commit();
    //                return result;
    //            } catch (Exception e) {
    //                System.err.println("XXX ERROR in runLocalAtomic: ");
    //                e.printStackTrace(System.err);
    //                throw new GridException("Grid transaction failed.", e);
    //            }
    //        });
    //    }

    @Override
    public <T> Future<Collection<? extends T>> runOnAll(
            Class<? extends GridTask<T>> taskClass,
            String arg,
            GridTxOptions opts) throws GridException {

        return doRunTask(
                taskClass.getName(),
                opts,
                () -> igniteGrid.getIgnite().compute().broadcast(
                        () -> IgniteGridServerTaskRunner.execute(
                                taskClass.getName(), arg)));
    }

    @Override
    public <T> Future<T> runOnOne(
            Class<? extends GridTask<T>> taskClass,
            String arg,
            GridTxOptions opts) throws GridException {

        return doRunTask(
                taskClass.getName(), opts,
                () -> igniteGrid.getIgnite().compute().call(
                        () -> IgniteGridServerTaskRunner.execute(
                                //TODO can we pass the class to execute instead of its string representation?
                                taskClass.getName(), arg)));
    }

    private boolean hasJobRan(String status) {
        return StringUtils.equalsAny(status, "done", "failed");
    }

    private <T> Future<T> doRunTask(
            String taskName,
            GridTxOptions opts,
            Callable<T> task) throws GridException {
        return CompletableFuture.supplyAsync(() -> {
            var taskRef = new MutableObject<Callable<T>>(task);
            if (opts.isAtomic()) {
                taskRef.setValue(() -> executeWithAtomic(taskRef.getValue()));
            }
            if (opts.isLock()) {
                taskRef.setValue(() -> executeWithLock(
                        opts.getName(), taskRef.getValue()));
            }
            try {
                return task.call();
            } catch (Exception e) {
                throw new GridException("Coult not run task: " + taskName, e);
            }
        });
    }

    private <T> T executeWithAtomic(Callable<T> task) {
        try (var tx = igniteGrid.getIgnite().transactions().txStart()) {
            var result = task.call();
            tx.commit();
            return result;
        } catch (Exception e) {
            throw new GridException("Transaction failed and rolled back", e);
        }
    }

    private <T> T executeWithLock(@NonNull String lockName, Callable<T> task) {
        var lock = igniteGrid.getIgnite().reentrantLock(
                lockName, true, false, true);
        lock.lock();

        try {
            return task.call();
        } catch (Exception e) {
            throw new GridException("Error during locked execution", e);
        } finally {
            lock.unlock();
        }
    }
}
