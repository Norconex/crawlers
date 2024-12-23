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

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public interface GridCompute {

    /**
     * Runs the supplied {@link Callable} only <b>once</b> for the entire crawl
     * session for the given job name. The execution takes place on the node
     * invoking this method. If
     * multiple nodes are invoking this method, only one of them will actually
     * execute the {@link Callable} while other nodes will block until
     * completion. Invoking this method more than once per crawl session
     * has no effect.
     * Because it is always executed by the calling node, this means no
     * serialization is required.
     * If you seek job execution fail-over, do not rely on this method.
     * The return value is a future with the return value of the callable
     * when on the node that executed it. Else, the future value is
     * <code>null</code>.
     * return {@link NullPointerException}
     * @param <T> Type of callable return value
     * @param jobName unique job name
     * @param callable code to execute
     * @return future with the callable return value (can be <code>null</code>)
     * @throws GridException problem with runnable execution
     */
    <T> Future<T> runLocalOnce(String jobName, Callable<T> callable)
            throws GridException;

    /**
     * Runs the supplied grid task on all grid nodes and have all node results
     * stored in a {@link Collection} in a {@link Future}.
     * @param <T> type of return value(s)
     * @param taskClass the task class to be instantiated and executed
     * @param arg argument to supply to the task instance
     * @param opts transaction options
     * @return a future holding  a collection of the executed code return
     *     (s), which can be empty if there is nothing to return by all
     *     instances ran.
     * @throws GridException
     */
    <T> Future<Collection<? extends T>> runOnAll(
            Class<? extends GridTask<T>> taskClass,
            String arg,
            GridTxOptions opts)
            throws GridException;

    /**
     * Runs the supplied grid task on one node and have the result
     * stored in a {@link Future}. Unlike
     * {@link #runLocalOnce(String, Callable)}, there is no guarantee on
     * which node the code will be executed. In addition, the same task can be
     * executed any number of times by callers.
     * @param taskClass the task class to be instantiated and executed
     * @param arg argument to supply to the task instance
     * @param opts transaction options
     * @return a future holding  a collection of the executed code return
     *     (s), which can be empty if there is nothing to return by all
     *     instances ran.
     * @throws GridException
     */
    <T> Future<T> runOnOne(
            Class<? extends GridTask<T>> taskClass,
            String arg,
            GridTxOptions opts)
            throws GridException;

}
