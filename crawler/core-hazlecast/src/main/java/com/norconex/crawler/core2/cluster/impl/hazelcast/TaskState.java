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

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents the state of a task in the cluster.
 */
public class TaskState implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Status {
        SUBMITTED, RUNNING, COMPLETED, FAILED
    }

    private final String taskId;
    private final Status status;
    private final String errorMessage;
    private final Instant timestamp;

    public TaskState(String taskId, Status status, String errorMessage) {
        this.taskId = Objects.requireNonNull(taskId, "Task ID cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.errorMessage = errorMessage;
        this.timestamp = Instant.now();
    }

    public String getTaskId() {
        return taskId;
    }

    public Status getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "TaskState [taskId=" + taskId 
            + ", status=" + status 
            + ", timestamp=" + timestamp
            + (errorMessage != null ? ", error=" + errorMessage : "")
            + "]";
    }
}