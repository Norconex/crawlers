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
package com.norconex.grid.core.impl.compute.work;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.jgroups.Address;
import org.jgroups.blocks.MethodCall;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RpcDispatcher;
import org.jgroups.util.RspList;

import com.norconex.grid.core.compute.ExecutionMode;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.TaskProgress;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

public class WorkDispatcher {

    private enum WorkerMethod {
        startNodeTask, //NOSONAR
        getNodeTaskProgress, //NOSONAR
        setGridTaskProgress, //NOSONAR
    }

    private final RpcDispatcher dispatcher;
    private final CoreGrid grid;

    public WorkDispatcher(CoreGrid grid, Worker worker) {
        this.grid = grid;
        dispatcher = new RpcDispatcher(grid.getChannel(), worker);
    }

    public void startTaskOnNodes(GridTask task) throws Exception {
        var call = MethodCallBuilder
                .create(WorkerMethod.startNodeTask)
                .argValues(task)
                .argTypes(GridTask.class)
                .build();
        var onOne = task.getExecutionMode() == ExecutionMode.SINGLE_NODE;
        dispatcher.callRemoteMethodsWithFuture(
                onOne ? List.of(grid.getCoordAddress()) : null,
                call,
                new RequestOptions(ResponseMode.GET_NONE, 0)
                        .setAnycasting(onOne));
    }

    public RspList<TaskProgress> getTaskProgressFromNodes(
            String taskId, Collection<Address> pendingNodes) throws Exception {
        var call = MethodCallBuilder
                .create(WorkerMethod.getNodeTaskProgress)
                .argValues(taskId)
                .argTypes(String.class)
                .build();
        return dispatcher.callRemoteMethods(
                new ArrayList<>(pendingNodes), //TODO why wrapping in ArrayList?
                call,
                new RequestOptions(ResponseMode.GET_ALL, 2000));
    }

    public void setGridTaskProgressOnNodes(
            String taskId, TaskProgress taskProgress) throws Exception {
        var call = MethodCallBuilder
                .create(WorkerMethod.setGridTaskProgress)
                .argValues(taskId, taskProgress)
                .argTypes(String.class, TaskProgress.class)
                .build();
        dispatcher.callRemoteMethods(
                null,
                call,
                new RequestOptions(ResponseMode.GET_NONE, 0));
    }

    @Accessors(fluent = true)
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class MethodCallBuilder {

        private final WorkerMethod method;
        private Object[] argValues = ArrayUtils.EMPTY_OBJECT_ARRAY;
        private Class<?>[] argTypes = ArrayUtils.EMPTY_CLASS_ARRAY;

        static MethodCallBuilder create(WorkerMethod method) {
            return new MethodCallBuilder(method);
        }

        MethodCallBuilder argValues(Object... argValues) {
            this.argValues = argValues;
            return this;
        }

        MethodCallBuilder argTypes(Class<?>... argTypes) {
            this.argTypes = argTypes;
            return this;
        }

        MethodCall build() {
            return new MethodCall(method.toString(), argValues, argTypes);
        }
    }
}
