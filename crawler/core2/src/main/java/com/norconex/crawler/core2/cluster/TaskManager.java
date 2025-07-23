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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

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
     *
     * @param taskName A unique name for the task.
     * @param task The Runnable task to execute.
     * @throws InterruptedException If the current thread is interrupted while
     *     waiting.
     * @throws TimeoutException If waiting for task completion times out (not
     *     implemented with explicit timeout here, but possible).
     * @throws ExecutionException could not execute task
     */
    void runOnOneOnceAndWait(String taskName, Runnable task)
            throws InterruptedException, TimeoutException, ExecutionException;

}