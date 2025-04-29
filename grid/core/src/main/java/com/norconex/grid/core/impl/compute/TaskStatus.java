package com.norconex.grid.core.impl.compute;

import java.io.Serializable;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class TaskStatus implements Serializable {// implements Streamable {

    private static final long serialVersionUID = 1L;

    private TaskState state;
    private Serializable result; // Changed from Object to String for simplicity
    private String error;

    /**
     * Create a task status with a pending state and no result and no error.
     */
    public TaskStatus() {
        this(TaskState.PENDING, null, null);
    }

    public TaskStatus(TaskState state, Serializable result, String error) {
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

    //    @Override
    //    public void writeTo(java.io.DataOutput out) throws java.io.IOException {
    //        out.writeInt(state.ordinal());
    //        org.jgroups.util.Util.writeString(result, out);
    //        org.jgroups.util.Util.writeString(error, out);
    //    }
    //
    //    @Override
    //    public void readFrom(java.io.DataInput in) throws java.io.IOException {
    //        state = TaskState.values()[in.readInt()];
    //        result = org.jgroups.util.Util.readString(in);
    //        error = org.jgroups.util.Util.readString(in);
    //    }
}
