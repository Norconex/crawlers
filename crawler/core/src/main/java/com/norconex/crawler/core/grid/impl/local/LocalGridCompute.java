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
package com.norconex.crawler.core.grid.impl.local;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.mutable.MutableObject;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.tx.TransactionStore;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;
import com.norconex.crawler.core.grid.GridTxOptions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes tasks locally.
 */
@Slf4j
@RequiredArgsConstructor
public class LocalGridCompute implements GridCompute {

    private final MVStore mvStore;
    private final String nodeId;

    @Override
    public <T> Future<T> runLocalOnce(String jobName, Callable<T> callable)
            throws GridException {
        var executor = Executors.newFixedThreadPool(1);
        try {
            return executor.submit(callable::call);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * <p>
     * {@inheritDoc}
     * About transaction options from {@link GridTxOptions}:
     * </p>
     * <ul>
     *   <li><b>Atomic</b>: Supported</li>
     *   <li>
     *     <b>Lock</b>: Ignored. Lock is enabled automatically when atomic is
     *     <code>true</code>. Can't change this behavior.
     *   </li>
     * </ul>
     */
    @Override
    public <T> Future<T> runOnOne(
            Class<? extends GridTask<T>> taskClass,
            String arg,
            GridTxOptions opts) throws GridException {
        return doRunTask(taskClass.getName(), opts, () -> {
            var gridTask = ClassUtil.newInstance(taskClass);
            return gridTask.run(getCrawlerContext(), arg);
        });
    }

    /**
     * Given a local grid has no concept of multiple nodes, this method
     * effectively behave like {@link #runOnOne(Class, String, GridTxOptions)}
     * with the difference of returning a collection of one element, or
     * an empty collection if the task result is <code>null</code>.
     */
    @Override
    public <T> Future<Collection<? extends T>> runOnAll(
            Class<? extends GridTask<T>> taskClass, String arg,
            GridTxOptions opts) throws GridException {
        return doRunTask(taskClass.getName(), opts, () -> {
            var gridTask = ClassUtil.newInstance(taskClass);
            var result = gridTask.run(getCrawlerContext(), arg);
            return result == null ? List.of() : List.of(result);
        });
    }

    private <T> Future<T> doRunTask(
            String taskName,
            GridTxOptions opts,
            Callable<T> task) throws GridException {
        return CompletableFuture.supplyAsync(() -> {
            getCrawlerContext().fire(CrawlerEvent.TASK_RUN_BEGIN, taskName);
            var taskRef = new MutableObject<Callable<T>>(task);
            if (opts.isAtomic()) {
                taskRef.setValue(() -> executeWithAtomic(taskRef.getValue()));
            }
            try {
                return task.call();
            } catch (Exception e) {
                throw new GridException("Coult not run task: " + taskName, e);
            } finally {
                getCrawlerContext().fire(CrawlerEvent.TASK_RUN_END, taskName);
            }
        });
    }

    private <T> T executeWithAtomic(Callable<T> task) {
        var txStore = new TransactionStore(mvStore);
        var tx = txStore.begin(); // Begin transaction
        try {
            var result = task.call();
            tx.commit();
            return result;
        } catch (Exception e) {
            tx.rollback();
            throw new GridException("Compute transaction rolled back due to an "
                    + "unexpected error.", e);
        }
    }

    private CrawlerContext getCrawlerContext() {
        var ctx = CrawlerContext.get(nodeId);
        if (ctx == null) {
            throw new IllegalStateException("Crawler context must be "
                    + "initialized before using local grid compute.");
        }
        return ctx;
    }
}
