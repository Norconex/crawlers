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
package com.norconex.crawler.core.grid.compute;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import com.norconex.crawler.core.grid.GridException;

import lombok.NonNull;

public interface GridCompute {

    //TODO add runSynchronized (only one at a time can run it)
    // or maybe... run atomic?

    public enum RunOn {
        ONE, ONE_ONCE, ALL, ALL_ONCE, ALL_SYNCHRONIZED
    }

    default <T> Future<T> runOn(
            @NonNull RunOn runOn,
            @NonNull String jobName,
            @NonNull Callable<T> callable) {
        return switch (runOn) {
            case ONE -> runOnOne(jobName, callable);
            case ONE_ONCE -> runOnOneOnce(jobName, callable);
            case ALL -> runOnAll(jobName, callable);
            case ALL_ONCE -> runOnAllOnce(jobName, callable);
            case ALL_SYNCHRONIZED -> runOnAllSynchronized(jobName, callable);
        };
    }

    /**
     * Runs the supplied grid task on one node and have the result
     * stored in a {@link Future}. Unlike
     * {@link #runOnOneOnce(String, Callable)}, the same task can be launch
     * multiple times within a crawl session (not at the same time),
     * but will always run on a single node.
     * @param jobName unique job name for the callable
     * @param callable code to execute
     * @return a future holding  a collection of the executed code return
     *     (s), which can be empty if there is nothing to return by all
     *     instances ran.
     * @throws GridException
     */
    <T> Future<T> runOnOne(String jobName, Callable<T> callable)
            throws GridException;

    /**
     * Runs the supplied {@link Callable} only <b>once</b> for the entire crawl
     * session for the given job name on exactly one node. If
     * multiple nodes are invoking this method, only one of them will actually
     * execute the {@link Callable} while other nodes will block until
     * completion or return right away without execution if the task has
     * already been completed. Invoking this method more than once per crawl
     * session has no effect.
     * Because it is always executed locally by the "chosen" node, this means no
     * serialization is required and a {@link Callable} instance is
     * passed as argument.
     * If you seek job execution fail-over (another node resuming the task
     * upon the chosen node failing), do not rely on this method.
     * The return value is a future that will contain the return value of the
     * {@link Callable} when on the node that executed it. Else, the future
     * value is <code>null</code>.
     * @param <T> Type of callable return value
     * @param jobName unique job name for the callable
     * @param callable code to execute
     * @return future with the callable return value (can be <code>null</code>)
     * @throws GridException problem with runnable execution
     */
    <T> Future<T> runOnOneOnce(String jobName, Callable<T> callable)
            throws GridException;

    /**
     * Runs the supplied grid task on all grid nodes and have all node results
     * stored in a {@link Collection} in a {@link Future} when done.
     * @param jobName unique job name for the runnable
     * @param runnable code to execute
     * @return a future holding  a collection of the executed code return
     *     (s), which can be empty if there is nothing to return by all
     *     instances ran.
     * @throws GridException
     */
    //TODO document not returning an array? Or is it?
    //TODO Document --> Return value is only for this nod, not all nodes
    // unless documented otherwise by specific implementations.
    <T> Future<T> runOnAll(String jobName, Callable<T> callable)
            throws GridException;

    //TODO Document --> Return value is only for this nod, not all nodes.
    <T> Future<T> runOnAllOnce(String jobName, Callable<T> callable)
            throws GridException;

    /**
     * Runs on all nodes, but only one at a time. Nodes wait for their
     * turn, but return immediately when done (does not wait for all nodes
     * to be done).
     * @param jobName unique job name for the runnable
     * @param runnable code to execute
     * @return a future
     * @throws GridException
     */
    //TODO document not returning an array? Or is it?
    //TODO Document --> Return value is only for this nod, not all nodes
    // unless documented otherwise by specific implementations.
    <T> Future<T> runOnAllSynchronized(String jobName, Callable<T> callable)
            throws GridException;
}
