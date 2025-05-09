package com.norconex.grid.core.compute;

import java.io.Serializable;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class TaskExecutionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private TaskState state;
    private Serializable result;
    private String error;

    /**
     * Create a task status with a pending state and no result and no error.
     */
    public TaskExecutionResult() {
        this(TaskState.PENDING, null, null);
    }

    public TaskExecutionResult(TaskState state, Serializable result, String error) {
        this.state = state;
        this.result = result;
        this.error = error;
    }

    public TaskState getState() {
        return state;
    }

    public Serializable getResult() {
        return result;
    }

    public String getError() {
        return error;
    }
}
