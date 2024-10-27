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

import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;
import com.norconex.crawler.core.grid.GridTxOptions;
import com.norconex.shaded.h2.mvstore.MVStore;
import com.norconex.shaded.h2.mvstore.tx.TransactionStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes tasks locally.
 */
@Slf4j
@RequiredArgsConstructor
public class LocalGridCompute implements GridCompute {

    private final MVStore mvStore;
    private final CrawlerContext crawlerContext;

    @Override
    public Future<?> runOnce(String jobName, Runnable runnable)
            throws GridException {
        var executor = Executors.newFixedThreadPool(1);
        try {
            return executor.submit(runnable::run);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * <p>
     * Only some transaction options are supported from {@link GridTxOptions}.
     * Details:
     * </p>
     * <ul>
     *   <li><b>Atomic</b>: Supported</li>
     *   <li>
     *     <b>Lock</b>: Ignored. Lock is enabled automatically when atomic is
     *     <code>true</code>. Can't change this behavior.
     *   </li>
     *   <li><b>Block</b>: Not applicable in a local grid.</li>
     *   <li><b>Singleton</b>: Not applicable in a local grid.</li>
     * </ul>
     */
    @Override
    public void runTask(
            Class<? extends GridTask> taskClass,
            String arg,
            GridTxOptions opts) throws GridException {

        var gridTask = ClassUtil.newInstance(taskClass);
        Runnable gridRunnable = () -> gridTask.run(crawlerContext, arg);
        if (opts.isAtomic()) {
            gridRunnable = withAtomic(gridRunnable);
        }
        crawlerContext.fire(CrawlerEvent.TASK_RUN_BEGIN, taskClass.getName());
        gridRunnable.run();
        crawlerContext.fire(CrawlerEvent.TASK_RUN_END, taskClass.getName());
    }

    private Runnable withAtomic(Runnable runnableWrapper) {
        // Also locks by default
        return () -> {
            var txStore = new TransactionStore(mvStore);
            var tx = txStore.begin(); // Begin transaction
            try {
                runnableWrapper.run();
                tx.commit();
            } catch (Exception e) {
                tx.rollback();
                LOG.error("Compute transaction rolled back due to an "
                        + "unexpected error.", e);
            }
        };
    }
}
