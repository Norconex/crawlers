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
package com.norconex.grid.core.compute;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.util.SerializableConsumer;
import com.norconex.grid.core.util.SerializableFunction;
import com.norconex.grid.core.util.SerializableRunnable;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * An builder-style alternative to creating a task over implementing
 * {@link GridTask} directly or extending {@link BaseGridTask}.
 */
@Setter
@Accessors(fluent = true)
public final class GridTaskBuilder implements Serializable {

    private static final long serialVersionUID = 1L;

    @Setter(value = AccessLevel.PRIVATE)
    private String id;
    private boolean once;
    /**
     * Defaults to {@link ExecutionMode#ALL_NODES}.
     */
    private ExecutionMode executionMode;
    private SerializableFunction<Grid, Serializable> executor;
    private SerializableFunction<List<TaskExecutionResult>,
            TaskExecutionResult> aggregator;
    private SerializableRunnable stopHandler;

    private GridTaskBuilder() {
    }

    public static GridTaskBuilder create(@NonNull String taskId) {
        return new GridTaskBuilder().id(taskId);
    }

    /**
     * Shortcut for setting {@link #executionMode(ExecutionMode)} to
     * {@link ExecutionMode#SINGLE_NODE}.
     * @return this, for chaining
     */
    public GridTaskBuilder singleNode() {
        executionMode = ExecutionMode.SINGLE_NODE;
        return this;
    }

    /**
     * Shortcut for setting {@link #executionMode(ExecutionMode)} to
     * {@link ExecutionMode#ALL_NODES}.
     * @return this, for chaining
     */
    public GridTaskBuilder allNodes() {
        executionMode = ExecutionMode.ALL_NODES;
        return this;
    }

    /**
     * Shortcut for setting {@link #once(boolean)} to <code>true</code>.
     * @return this, for chaining
     */
    public GridTaskBuilder once() {
        once = true;
        return this;
    }

    /**
     * An executor that will execute a task and return a result.
     * It is given a context, as specified by the grid consuming node.
     * @param executor the executor
     * @return this, for chaining
     */
    public GridTaskBuilder executor(
            SerializableFunction<Grid, Serializable> executor) {
        this.executor = executor;
        return this;
    }

    /**
     * A processor that will process a task without returning a result.
     * It is given a context, as specified by the grid consuming node.
     * When invoked, {@link GridTask#execute(Grid)} will
     * return {@code null}.
     * @param processor the processor
     * @return this, for chaining
     */
    public GridTaskBuilder processor(SerializableConsumer<Grid> processor) {
        executor = grid -> {
            processor.accept(grid);
            return null;
        };
        return this;
    }

    public BaseGridTask build() {
        Objects.requireNonNull(id, "'id' must not be null.");
        Objects.requireNonNull(executor, "'executor' must not be null.");
        return new BaseGridTask(id, executionMode) {
            private static final long serialVersionUID = 1L;

            @Override
            public void stop() {
                if (stopHandler != null) {
                    stopHandler.run();
                } else {
                    super.stop();
                }
            }

            @Override
            public boolean isOnce() {
                return once;
            }

            @Override
            public Serializable execute(Grid grid) {
                return executor.apply(grid);
            }

            @Override
            public TaskExecutionResult
                    aggregate(List<TaskExecutionResult> results) {
                if (aggregator != null) {
                    return aggregator.apply(results);
                }
                return super.aggregate(results);
            }
        };
    }
}
