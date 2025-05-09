package com.norconex.grid.core.impl.compute.task;

import java.io.Serializable;

import com.norconex.grid.core.compute.TaskExecutionResult;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class TaskProgress implements Serializable {
    private static final long serialVersionUID = 1L;

    private TaskExecutionResult status;
    private long lastHeartbeat;

    public TaskProgress() {
    }

    public TaskProgress(TaskExecutionResult status, long lastHeartbeat) {
        this.status = status;
        this.lastHeartbeat = lastHeartbeat;
    }

    public TaskExecutionResult getStatus() {
        return status;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }
}
