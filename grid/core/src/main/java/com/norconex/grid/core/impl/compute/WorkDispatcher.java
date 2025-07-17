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
package com.norconex.grid.core.impl.compute;

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
import com.norconex.grid.core.compute.TaskExecutionResult;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.task.TaskProgress;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WorkDispatcher {

    private enum WorkerMethod {
        startNodeTask, //NOSONAR
        stopNodeTask, //NOSONAR
        getNodeTaskProgress, //NOSONAR
        setGridTaskProgress, //NOSONAR
        clearTaskStatus, //NOSONAR
        setPipelineDone, //NOSONAR
        stopPipeline, //NOSONAR
    }

    private final RpcDispatcher dispatcher;
    private final CoreGrid grid;

    public WorkDispatcher(CoreGrid grid, Worker worker) {
        this.grid = grid;
        dispatcher = new RpcDispatcher(grid.getChannel(), worker);
    }

    /**
     * Starts the given task on the address supplied, ONLY if execution
     * mode is ALL_NODES or it is the coordinator. You should favor
     * {@link #startTaskOnNodes(GridTask)} and use this one mainly
     * for joining nodes.
     * @param task the task to execute
     * @param address the node to potentially execute the task on
     * @throws Exception error in execution
     */
    public void startTaskOnNode(GridTask task, Address address)
            throws Exception {
        if (task.getExecutionMode() == ExecutionMode.ALL_NODES ||
                (task.getExecutionMode() == ExecutionMode.SINGLE_NODE
                        && grid.isCoordinator())) {
            var call = MethodCallBuilder
                    .create(WorkerMethod.startNodeTask)
                    .argValues(task)
                    .argTypes(GridTask.class)
                    .build();
            dispatcher.callRemoteMethodWithFuture(
                    address,
                    call,
                    new RequestOptions(ResponseMode.GET_NONE, 0));
        } else {
            LOG.debug("Trying to start a single-node task on a "
                    + "non-coordinator. Ignoring request.");
        }
    }

    /**
     * Starts the given task on one or more nodes.
     * @param task the task to execute
     * @throws Exception error in execution
     */
    public void startTaskOnNodes(GridTask task) throws Exception {
        var call = MethodCallBuilder
                .create(WorkerMethod.startNodeTask)
                .argValues(task)
                .argTypes(GridTask.class)
                .build();
        if (task.getExecutionMode() == ExecutionMode.SINGLE_NODE) {
            dispatcher.callRemoteMethodWithFuture(
                    grid.getCoordAddress(),
                    call,
                    new RequestOptions(ResponseMode.GET_NONE, 0));

        } else {
            dispatcher.callRemoteMethodsWithFuture(
                    null,
                    call,
                    new RequestOptions(ResponseMode.GET_NONE, 0));
        }
    }

    public void stopTaskOnNodes(String taskId) throws Exception {
        var call = MethodCallBuilder
                .create(WorkerMethod.stopNodeTask)
                .argValues(taskId)
                .argTypes(String.class)
                .build();
        dispatcher.callRemoteMethods(
                null, call, new RequestOptions(ResponseMode.GET_NONE, 0));
    }

    public RspList<TaskProgress> getTaskProgressFromNodes(
            String taskId, Collection<Address> pendingNodes) throws Exception {
        var call = MethodCallBuilder
                .create(WorkerMethod.getNodeTaskProgress)
                .argValues(taskId)
                .argTypes(String.class)
                .build();
        return dispatcher.callRemoteMethods(
                new ArrayList<>(pendingNodes),
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

    public void clearTaskStatusOnNodes(String taskId) throws Exception {
        var call = MethodCallBuilder
                .create(WorkerMethod.clearTaskStatus)
                .argValues(taskId)
                .argTypes(String.class)
                .build();
        dispatcher.callRemoteMethods(
                null, call, new RequestOptions(ResponseMode.GET_NONE, 0));
    }

    public void setPipelineDoneOnNodes(
            String pipelineId, TaskExecutionResult result) throws Exception {
        var call = MethodCallBuilder
                .create(WorkerMethod.setPipelineDone)
                .argValues(pipelineId, result)
                .argTypes(String.class, TaskExecutionResult.class)
                .build();
        dispatcher.callRemoteMethods(
                null, call, new RequestOptions(ResponseMode.GET_NONE, 0));
    }

    // only notifies coord, who will stop pipe + all tasks
    public void stopPipeline(String pipelineId) throws Exception {
        var call = MethodCallBuilder
                .create(WorkerMethod.stopPipeline)
                .argValues(pipelineId)
                .argTypes(String.class)
                .build();
        dispatcher.callRemoteMethods(
                List.of(grid.getCoordAddress()),
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
