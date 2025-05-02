package com.norconex.grid.core.impl.compute.task;

import java.io.Serializable;

import com.norconex.grid.core.compute.TaskStatus;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class TaskProgress implements Serializable {// Streamable {
    private static final long serialVersionUID = 1L;

    private TaskStatus status;
    private long lastHeartbeat;

    public TaskProgress() {
    }

    public TaskProgress(TaskStatus status, long lastHeartbeat) {
        this.status = status;
        this.lastHeartbeat = lastHeartbeat;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }
}
