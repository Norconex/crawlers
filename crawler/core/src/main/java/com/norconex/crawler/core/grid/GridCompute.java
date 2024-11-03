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
import java.util.concurrent.Future;

public interface GridCompute {

    /**
     * Runs the supplied {@link Runnable} only once for the entire crawl
     * session on only one node, which is the node invoking this method. If
     * multiple nodes are invoking this method, only one of them will actually
     * execute the {@link Runnable} while other nodes will block until
     * completion.
     * Because it is always executed by the calling node, this means no
     * serialization is required.
     * @param jobName unique job name
     * @param runnable code to execute
     * @return a future without a value (<code>null</code>)
     * @throws GridException problem with runnable execution
     */
    Future<?> runOnceOnLocal(String jobName, Runnable runnable)
            throws GridException;

    //    void runTask_ORIGINAL(
    //            Class<? extends GridTask> taskClass,
    //            String arg,
    //            GridTxOptions opts)
    //            throws GridException;

    //TODO if we go with something like this as the base class
    // for everything else, also modify GridTask
    //    <T> FutureTask<Collection<? extends T>> runTask(
    //            Class<? extends GridTask> taskClass,
    //            String arg,
    //            GridTxOptions opts)
    //            throws GridException;

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
     * {@link #runOnceOnLocal(String, Runnable)}, there is no guarantee on
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
