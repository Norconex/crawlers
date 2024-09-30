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
package com.norconex.crawler.core.grid;

import lombok.NonNull;

//GridCompute interface for compute-related operations
public interface GridCompute {

    void runTask(
            @NonNull Class<? extends GridTask> taskClass,
            @NonNull String taskName,
            @NonNull GridTxOptions options) throws GridException;

    //    // Broadcast a task to all nodes in the cluster
    //    void broadcastTask(Runnable job);
    //
    //    // Run a task on any available node (without waiting for results)
    //    void runTask(Runnable job);
    //
    //    broadcastTask(task: () => void): void;
    //
    //    runTask(task: () => void): void;
    //
    //    // Run a task that returns a result
    //    <T> runTaskWithResult(task: () => T): Promise<T>;
    //
    //    // Run a task across multiple nodes and gather results
    //    <T> runDistributedTask(task: () => T): Promise<T[]>;
}
