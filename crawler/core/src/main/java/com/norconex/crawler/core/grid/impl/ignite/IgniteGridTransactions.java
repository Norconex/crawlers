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
package com.norconex.crawler.core.grid.impl.ignite;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.apache.commons.lang3.function.FailableRunnable;

import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTransactions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class IgniteGridTransactions implements GridTransactions {

    private final IgniteGrid igniteGrid;

    @Override
    public <T> T runInTransaction(Callable<T> callable)
            throws GridException {
        return igniteGrid.api().transactions().runInTransaction(tx -> {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new GridException(
                        "A problem occured during transaction execution.", e);
            }
        });
    }

    @Override
    public Future<Void> runInTransactionAsync(
            FailableRunnable<Exception> runnable)
            throws GridException {
        return igniteGrid
                .api()
                .transactions()
                .runInTransactionAsync(tx -> {
                    try {
                        runnable.run();
                        return null;
                    } catch (Exception e) {
                        throw new GridException(
                                "A problem occured during transaction execution.",
                                e);
                    }
                });
    }
}
