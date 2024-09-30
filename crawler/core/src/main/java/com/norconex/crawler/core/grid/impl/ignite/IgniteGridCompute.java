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

import org.apache.ignite.Ignite;
import org.apache.ignite.lang.IgniteFuture;

import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;
import com.norconex.crawler.core.grid.GridTxOptions;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class IgniteGridCompute implements GridCompute {

    private final Ignite ignite;

    @Override
    public void runTask(//GridTaskContext ctx)
            Class<? extends GridTask> taskClass, String taskName,
            GridTxOptions opts)
            throws GridException {

        var className = taskClass.getName();
        System.err.println("CLLLLLLLLLLLLLLLLLLLLAAAASS NAME IS: " + className);
        var gridRunnable = gridRunnable(className, taskName, opts);

        if (opts.isAtomic()) {
            gridRunnable = withAtomic(gridRunnable);
        }

        if (opts.isLock()) {
            gridRunnable = withLock(opts.getName(), gridRunnable);
        }

        gridRunnable.run();

        //        final var finalTask = task;
        //        IgniteFuture<Void> future;
        //
        //        if (opts.isSingleton()) {
        //            future = ignite.compute().runAsync(
        //                    () -> IgniteServerGridTaskExecutor.execute(className, arg));//  finalTask::run);
        //            //            future = ignite.services().deployClusterSingletonAsync(name,
        //            //                    service);
        //        } else {
        //            //NOTE: deployMultipleAsync is independent on each nodes so
        //            // the total count is multiplied by the number of app instances.
        //            // to prevent this, we wrap the multi call in a singleton call.
        //            // This ensure only one request for running multiple requests
        //            // is made
        //
        //            // TODO take values from ignite config?
        //            //            ignite.services().clusterGroup().
        //            future = ignite.compute().broadcastAsync(
        //                    () -> IgniteServerGridTaskExecutor.execute(className, arg)); //finalTask::run);
        //            //
        //            //            future = ignite.services().deployMultipleAsync(name,
        //            //                    service, 0, 1);
        //        }
        //
        //        if (opts.isBlock()) {
        //            future.get(); // blocks until completion
        //        }
    }

    private Runnable gridRunnable(String className, String taskName,
            GridTxOptions opts) {
        return () -> {
            IgniteFuture<Void> future;

            if (opts.isSingleton()) {
                future = ignite.compute()
                        .runAsync(() -> IgniteServerTaskExecutor
                                .execute(className, taskName));//  finalTask::run);
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
                future = ignite.compute()
                        .broadcastAsync(() -> IgniteServerTaskExecutor
                                .execute(className, taskName)); //finalTask::run);
                //
                //            future = ignite.services().deployMultipleAsync(name,
                //                    service, 0, 1);
            }

            if (opts.isBlock()) {
                future.get(); // blocks until completion
            }
        };
    }

    //    private IgniteRunnable serverRunnable(String className, String arg) {
    //        return () -> IgniteServerGridTaskExecutor.execute(className, arg);
    //    }

    private Runnable withLock(String name, Runnable runnableWrapper) {
        return () -> {
            var lock = ignite.reentrantLock(name + "-lock", true, true, true);
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
            try (var tx = ignite.transactions().txStart()) {
                runnableWrapper.run();
                tx.commit();
            }
        };
    }

}
