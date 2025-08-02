/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core2.cluster.impl.hazelcast;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.map.IMap;
import com.norconex.crawler.core2.cluster.ClusterTask;
import com.norconex.crawler.core2.cluster.TaskException;
import com.norconex.crawler.core2.cluster.TaskManager;

/**
 * Hazelcast implementation of the TaskManager interface.
 */
public class HazelcastTaskManager implements TaskManager {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastTaskManager.class);
    
    private final HazelcastInstance hazelcastInstance;
    private final IExecutorService executorService;
    private final IMap<String, TaskState> taskStates;
    
    public HazelcastTaskManager(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = Objects.requireNonNull(
                hazelcastInstance, "Hazelcast instance cannot be null");
        this.executorService = hazelcastInstance.getExecutorService("nx-task-executor");
        this.taskStates = hazelcastInstance.getMap("nx-task-states");
        LOG.info("Hazelcast task manager initialized");
    }
    
    @Override
    public <T> CompletableFuture<T> submitTask(ClusterTask<T> task) {
        Objects.requireNonNull(task, "Task cannot be null");
        String taskId = UUID.randomUUID().toString();
        
        LOG.debug("Submitting task {} of type {}", taskId, task.getClass().getSimpleName());
        
        // Create a new CompletableFuture to track the task execution
        CompletableFuture<T> future = new CompletableFuture<>();
        
        // Store initial task state
        taskStates.put(taskId, new TaskState(taskId, TaskState.Status.SUBMITTED, null));
        
        try {
            // Submit the task to the executor service
            executorService.submit(() -> {
                try {
                    LOG.debug("Executing task {}", taskId);
                    taskStates.put(taskId, new TaskState(taskId, TaskState.Status.RUNNING, null));
                    
                    // Execute the task
                    T result = task.call();
                    
                    LOG.debug("Task {} completed successfully", taskId);
                    taskStates.put(taskId, new TaskState(taskId, TaskState.Status.COMPLETED, null));
                    
                    // Complete the future with the result
                    future.complete(result);
                    return result;
                } catch (Exception e) {
                    LOG.error("Task {} failed with exception", taskId, e);
                    taskStates.put(taskId, new TaskState(taskId, TaskState.Status.FAILED, 
                            e.getMessage()));
                    
                    // Complete the future exceptionally
                    future.completeExceptionally(e);
                    throw new TaskException("Task execution failed", e);
                }
            });
            
            return future;
        } catch (Exception e) {
            LOG.error("Failed to submit task {}", taskId, e);
            taskStates.put(taskId, new TaskState(taskId, TaskState.Status.FAILED, e.getMessage()));
            future.completeExceptionally(e);
            throw new TaskException("Failed to submit task", e);
        }
    }
    
    @Override
    public void shutdown() {
        LOG.info("Shutting down Hazelcast task manager...");
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
    
    @Override
    public Map<String, Object> getTaskStatuses() {
        return taskStates.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> (Object) entry.getValue()));
    }
}