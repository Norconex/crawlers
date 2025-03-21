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
package com.norconex.crawler.core.grid.impl.local;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.commons.lang3.function.FailableRunnable;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.tx.TransactionStore;

import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTransactions;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class LocalGridTransactions implements GridTransactions {

    private final MVStore mvStore;

    @Override
    public <T> T runInTransaction(Callable<T> callable) throws GridException {
        var txStore = new TransactionStore(mvStore);
        var tx = txStore.begin();
        try {
            var result = callable.call();
            tx.commit();
            return result;
        } catch (Exception e) {
            tx.rollback();
            throw new GridException(
                    "A problem occured during transaction execution.", e);
        }
    }

    @Override
    public Future<Void> runInTransactionAsync(
            FailableRunnable<Exception> runnable)
            throws GridException {
        return CompletableFuture.supplyAsync(() -> {
            var txStore = new TransactionStore(mvStore);
            var tx = txStore.begin();
            try {
                runnable.run();
                tx.commit();
                return null;
            } catch (Exception e) {
                tx.rollback();
                throw new GridException(
                        "A problem occured during transaction execution.", e);
            }
        });
    }
}
