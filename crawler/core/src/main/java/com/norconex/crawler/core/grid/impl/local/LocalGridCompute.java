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

import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;
import com.norconex.crawler.core.grid.GridTxOptions;

/**
 * Executes tasks locally.
 */
public class LocalGridCompute implements GridCompute {

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

    @Override
    public void runTask(Class<? extends GridTask> taskClass, String arg,
            GridTxOptions opts) throws GridException {
        // TODO Auto-generated method stub

    }
}
