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
 * wait for a job to complete before returning whether it runs on
 * one or all nodes. To have it behave differently, have your job run in a
 * thread and return right away.
 */
public interface GridCompute {

    //TODO add stop here or on grid

    public enum RunOn {
        ONE, ONE_ONCE, ALL, ALL_ONCE
    }

    /**
     * Runs the supplied job on one or multiple nodes as per the {@link RunOn}
     * value being passed. Each {@link RunOn} options correspond to
     * a compute method.
     * @param runOn how to run the job on a grid
     * @param jobName job name
     * @param runnable job to execute
     * @return job state when execution terminates
     */
    default GridJobState runOn(
            @NonNull RunOn runOn,
            @NonNull String jobName,
            @NonNull Runnable runnable) {
        return switch (runOn) {
            case ONE -> runOnOne(jobName, runnable);
            case ONE_ONCE -> runOnOneOnce(jobName, runnable);
            case ALL -> runOnAll(jobName, runnable);
            case ALL_ONCE -> runOnAllOnce(jobName, runnable);
        };
    }

    /**
     * Runs the supplied job on a single node and return the execution status.
     * The same job can be launched multiple times within a crawl session,
     * but not concurrently.
     * @param jobName job name
     * @param runnable job to execute
     * @return job state when execution terminates
     * @throws GridException problem with runnable execution
     */
    GridJobState runOnOne(String jobName, Runnable runnable)
            throws GridException;

    /**
     * Runs the supplied job on a single node and return the execution status.
     * The same job cannot be run more than once within a crawl session.
     * Invoking this method more than once per crawl session with the
     * same job name has no effect.
     * @param jobName job name
     * @param runnable job to execute
     * @return job state when execution terminates
     * @throws GridException problem with runnable execution
     */
    GridJobState runOnOneOnce(String jobName, Runnable runnable)
            throws GridException;

    /**
     * Runs the supplied job on all nodes and return the execution status.
     * The same job can be launched multiple times within a crawl session,
     * but not concurrently.
     * If at least one of the nodes completes normally, the job as a whole
     * is considered to have completed normally.
     * @param jobName job name
     * @param runnable job to execute
     * @return job state when execution terminates
     * @throws GridException problem with runnable execution
     */
    GridJobState runOnAll(String jobName, Runnable runnable)
            throws GridException;

    /**
     * Runs the supplied job on all nodes and return the execution status.
     * The same job cannot be run more than once within a crawl session.
     * Invoking this method more than once per crawl session with the
     * same job name has no effect.
     * If at least one of the nodes completes normally, the job as a whole
     * is considered to have completed normally.
     * @param jobName job name
     * @param runnable job to execute
     * @return job state when execution terminates
     * @throws GridException problem with runnable execution
     */
    GridJobState runOnAllOnce(String jobName, Runnable runnable)
            throws GridException;

    /**
     * Requests for the execution of a running job implementing
     * {@link StoppableRunnable}, or all such running jobs if the
     * supplied job name is <code>null</code>. If no matching jobs
     * are currently running, invoking this method has no effect. This method
     * returns right away and lets the jobs handle the request.
     *
     * @param jobName the job to stop, or <code>null</code>
     */
    void requestStop(String jobName);
}
