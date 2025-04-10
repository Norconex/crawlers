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
package com.norconex.grid.core.compute;

import com.norconex.grid.core.GridException;

import lombok.NonNull;

/**
 * Grid compute methods. All methods run synchronously. All nodes on a grid
 * wait for a task to complete before returning whether it runs on
 * one or all nodes. To have it behave differently, have your task run in a
 * thread and return right away.
 */
public interface GridCompute {

    //TODO add stop here or on grid

    public enum RunOn {
        ONE, ONE_ONCE, ALL, ALL_ONCE;

        public boolean isOnAll() {
            return this == ALL || this == ALL_ONCE;
        }

        public boolean isOnOne() {
            return !isOnAll();
        }

        public boolean isOnce() {
            return this == ONE_ONCE || this == ALL_ONCE;
        }
    }

    /**
     * Runs the supplied task on one or multiple nodes as per the {@link RunOn}
     * value being passed. Each {@link RunOn} options correspond to
     * a compute method.
     * @param runOn how to run the task on a grid
     * @param taskName task name
     * @param task task to execute
     * @return task state when execution terminates
     */
    default <T> GridComputeResult<T> runOn(
            @NonNull RunOn runOn,
            @NonNull String taskName,
            @NonNull GridComputeTask<T> task) {
        return switch (runOn) {
            case ONE -> runOnOne(taskName, task);
            case ONE_ONCE -> runOnOneOnce(taskName, task);
            case ALL -> runOnAll(taskName, task);
            case ALL_ONCE -> runOnAllOnce(taskName, task);
        };
    }

    /**
     * Runs the supplied Runnable on one or multiple nodes as per the
     * {@link RunOn} value being passed. Each {@link RunOn} options correspond
     * to a compute method.
     * @param runOn how to run the task on a grid
     * @param taskName task name
     * @param runnable runnable to execute
     * @return task state when execution terminates
     */
    default <T> GridComputeResult<T> runOn(
            @NonNull RunOn runOn,
            @NonNull String taskName,
            @NonNull Runnable runnable) {
        return runOn(runOn, taskName, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Runs the supplied task on a single node and return the execution status.
     * The same task can be launched multiple times within a crawl session,
     * but not concurrently.
     * @param taskName task name
     * @param task task to execute
     * @return task state when execution terminates
     * @throws GridException problem with runnable execution
     */
    <T> GridComputeResult<T> runOnOne(
            String taskName, GridComputeTask<T> task) throws GridException;

    /**
     * Runs the supplied task on a single node and return the execution status.
     * The same task cannot be run more than once within a crawl session.
     * Invoking this method more than once per crawl session with the
     * same task name has no effect.
     * @param taskName task name
     * @param task task to execute
     * @return task state when execution terminates
     * @throws GridException problem with runnable execution
     */
    <T> GridComputeResult<T> runOnOneOnce(
            String taskName, GridComputeTask<T> task) throws GridException;

    /**
     * Runs the supplied task on all nodes and return the execution status.
     * The same task can be launched multiple times within a crawl session,
     * but not concurrently.
     * If at least one of the nodes completes normally, the task as a whole
     * is considered to have completed normally.
     * @param taskName task name
     * @param task task to execute
     * @return task state when execution terminates
     * @throws GridException problem with runnable execution
     */
    <T> GridComputeResult<T> runOnAll(
            String taskName, GridComputeTask<T> task) throws GridException;

    /**
     * Runs the supplied task on all nodes and return the execution status.
     * The same task cannot be run more than once within a crawl session.
     * Invoking this method more than once per crawl session with the
     * same task name has no effect.
     * If at least one of the nodes completes normally, the task as a whole
     * is considered to have completed normally.
     * @param taskName task name
     * @param task task to execute
     * @return task state when execution terminates
     * @throws GridException problem with runnable execution
     */
    <T> GridComputeResult<T> runOnAllOnce(
            String taskName, GridComputeTask<T> task) throws GridException;

    /**
     * Requests for the execution of a running task or all running tasks
     * if the supplied task name is <code>null</code>. If no matching tasks
     * are currently running, invoking this method has no effect. This method
     * returns right away and lets the tasks handle the request.
     *
     * @param taskName the task to stop, or <code>null</code>
     */
    void stop(String taskName);
}
