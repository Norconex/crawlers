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
package com.norconex.crawler.core.store;

import org.apache.commons.lang3.function.FailableRunnable;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Executes data store operations or arbitrary code atomically.
 * That is, multiple operations are considered a single operation.
 * Useful in a clustered or multi-threaded environment where there could
 * otherwise be concurrency issues.
 * </p>
 * <p>
 * Data store engine implementations are not required to provide any
 * of the offered atomic transaction options. They will simply execute without
 * atomicity.  Those implementations are usually best on single-node
 * installations.
 * </p>
 */
@Data
@Accessors(fluent = true)
public abstract class DataStoreGridExecutor {

    //TODO pass interface in constructor instead of abstract?

    /**
     * Whether to make the execution atomic. Usually safer but less efficient
     * so use only when needed, such as when you have multiple store operations
     * that should be considered as a single unit.
     */
    private boolean atomic;
    /**
     * Whether only one store client <b>at a time</b> can execute. Locked
     * executors on a multi-node environment will execute sequentially unless
     * "singleton" is <code>true</code>.
     */
    private boolean lock;
    /**
     * Whether to block all store clients execution until execution is
     * completed on all nodes. When singleton is <code>true</code> all nodes
     * will wait for the single instance executing to be done.
     */
    private boolean block;
    /**
     * Whether only one store client can execute. Other clients will
     * wait until completion only if "block" is <code>true</code>.
     * Setting "lock" to <code>true</code> has no effect when "singleton" is
     * <code>true</code>.
     */
    private boolean singleton;

    /**
     * Whether an execution of the same name can be run again within
     * a crawl session. This is mainly useful if we have a late-joiner node
     * to a cluster and we don't want it to start from scratch, running
     * services that should only run once (regardless on how many clusters).
     */
    private boolean once;

    /**
     * Executes the callable atomically. Checked exceptions are wrapped
     * into a {@link DataStoreException}.
     * @param <T> type of returned value
     * @param name executor/transaction name
     * @param callable the code to execute
     * @return any return value, including <code>null</code>
     */
    public abstract void execute(
            String name, FailableRunnable<Exception> callable);
}
