/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.grid.core;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.storage.GridStorage;

/**
 * Underlying system used to compute tasks and store crawl session data.
 */
public interface Grid extends Closeable {

    //MAYBE: use SPI to detect which grid/storage implementation to use
    // but also offer to optionally pass one in constructor instead.

    GridCompute getCompute();

    GridStorage getStorage();

    /**
     * Holds context data specific to each grid instances.
     * @return grid context
     */
    // GridContext getGridContext();

    /**
     * Logical name unique to each node in a cluster.
     * @return unique node name
     */
    String getNodeName();

    /**
     * The name of the grid we are connected to.
     * @return grid name
     */
    String getGridName();

    /**
     * Gets rid of persisted state information about running jobs and any
     * other bits of information the grid implementation associates with the
     * concept of a "session".  For instance, jobs launched on the grid
     * compute "ONCE" will be able to be launched again.
     * Invoking this method does not delete other persisted data and leaves
     * stores created by consuming applications intact.
     */
    void resetSession();

    /**
     * <p>
     * Stop the grid execution. An attempt is made to gracefully stop by
     * notifying each running pipelines or compute tasks of the stop request.
     * In addition, running pipelines won't advance to their next stages.
     * </p>
     */
    void stop();

    /**
     * Closes the local grid connection, releasing any local resources
     * associated to it. If there are still active nodes running pipelines or
     * jobs on the grid, they'll keep running. A stop
     * request can be made instead to shutdown the entire grid.
     */
    @Override
    void close();

    /**
     * Asynchronously waits until at least the specified number of nodes
     * have joined the grid, or until the timeout is reached.
     * <p>
     * This can be used to coordinate actions that require a minimum number
     * of participants in the cluster before proceeding. If the condition is
     * already met at the time of invocation, the returned future completes
     * immediately.
     * </p>
     *
     * @param count the minimum number of nodes required to consider the grid
     *        ready
     * @param timeout the maximum duration to wait for the condition to be met
     * @return a {@link CompletableFuture} that completes when the specified
     *         number of nodes have joined the grid, or completes exceptionally
     *         with a {@link TimeoutException} if the timeout expires first
     *
     * @throws IllegalArgumentException if {@code count <= 0}
     *         or {@code timeout <= 0}
     */
    CompletableFuture<Void> awaitMinimumNodes(int count, Duration timeout);

    /**
     * Register a context object that will be available locally on this node,
     * and passed as argument to grid tasks referencing its key. Can be any
     * object type.
     * @param contextKey A unique identifier for this context
     * @param context The context object (not serialized)
     */
    void registerContext(String contextKey, Object context);

    /**
     * Get a registered context object by its key.
     * @param contextKey The unique identifier of a registered context
     * @return the associated context object, or {@code null} if none
     *       is none could be found under the given key.
     */
    Object getContext(String contextKey);

    /**
     * Unregister a context object that is no longer needed by any task
     * @param contextKey The unique identifier of a registered context
     * @return the object removed, if any
     */
    Object unregisterContext(String contextKey);
}
