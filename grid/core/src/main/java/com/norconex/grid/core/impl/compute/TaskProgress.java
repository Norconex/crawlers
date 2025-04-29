package com.norconex.grid.core.impl.compute;

import java.io.Serializable;

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

    //    @Override
    //    public void writeTo(java.io.DataOutput out) throws IOException {
    //        Util.writeStreamable(status, out);
    //        out.writeLong(lastHeartbeat);
    //    }
    //
    //    @Override
    //    public void readFrom(java.io.DataInput in) throws IOException {
    //        try {
    //            status = Util.readStreamable(TaskStatus::new, in);
    //        } catch (ClassNotFoundException e) {
    //            throw new IOException(e);
    //        }
    //        lastHeartbeat = in.readLong();
    //    }
}
