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
package com.norconex.crawler.core2.cluster;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface TaskManager {

    /**
     * Executes a given task exactly once across the cluster.
     *
     * If this node is the one to run the task:
     * - It acquires a distributed lock.
     * - It sets the task status to RUNNING.
     * - It executes the task.
     * - It sets the task status to COMPLETED (or FAILED).
     * - It releases the lock.
     *
     * If another node is running the task or has already completed it:
     * - If completed, this method returns immediately.
     * - If running, this method waits until the task is completed by the other
     *   node.
     * @param <T> type of returned value
     * @param taskName A unique name for the task.
     * @param task The Runnable task to execute.
     * @return value created by the task
     * @throws TaskException error while running task
     */
    <T> Optional<T> runOnOneOnceSync(String taskName, ClusterTask<T> task)
            throws TaskException;

    /**
     * Executes a given task exactly once across the cluster and returns a
     * CompletableFuture for asynchronous handling.
     *
     * If this node is the one to run the task:
     * - It acquires a distributed lock.
     * - It sets the task status to RUNNING.
     * - It executes the task.
     * - It sets the task status to COMPLETED (or FAILED).
     * - It releases the lock.
     *
     * If another node is running the task or has already completed it:
     * - If completed, the future completes immediately.
     * - If running, the future will complete when the task is completed by another node.
     *
     * @param <T> type of returned value
     * @param taskName A unique name for the task.
     * @param task The Runnable task to execute.
     * @return CompletableFuture that completes with the task's result
     */
    <T> CompletableFuture<Optional<T>> runOnOneOnceAsync(String taskName,
            ClusterTask<T> task);

    <T> Optional<T> runOnOneSync(String taskName, ClusterTask<T> task)
            throws TaskException;

    <T> CompletableFuture<Optional<T>> runOnOneAsync(String taskName,
            ClusterTask<T> task);

    <T, R> CompletableFuture<R> runOnAllOnceAsync(
            String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer);

    <T, R> R runOnAllOnceSync(
            String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer);

    <T, R> CompletableFuture<R> runOnAllAsync(
            String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer);

    <T, R> R runOnAllSync(
            String taskName,
            ClusterTask<T> task,
            ClusterReducer<T, R> reducer);

    void stopTask(String taskName);
}
