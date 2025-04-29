package com.norconex.grid.core.impl.compute;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import org.junit.jupiter.api.Test;

import com.norconex.grid.core.impl.compute.TaskState;
import com.norconex.grid.core.impl.compute.TaskStatus;
import com.norconex.grid.core.impl_DELETE.compute.task.TaskProgress;

public class TaskStatusSerializationTest {
    @Test
    public void testTaskStatusSerialization() throws Exception {
        TaskStatus status = new TaskStatus(TaskState.COMPLETED, "test result",
                "test error");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        status.writeTo(out);
        out.close();

        ByteArrayInputStream bais =
                new ByteArrayInputStream(baos.toByteArray());
        DataInputStream in = new DataInputStream(bais);
        TaskStatus deserialized = new TaskStatus();
        deserialized.readFrom(in);
        in.close();

        Assert.assertEquals("State should match", TaskState.COMPLETED,
                deserialized.getState());
        Assert.assertEquals("Result should match", "test result",
                deserialized.getResult());
        Assert.assertEquals("Error should match", "test error",
                deserialized.getError());
    }

    @Test
    public void testTaskProgressSerialization() throws Exception {
        TaskProgress progress = new TaskProgress(
                new TaskStatus(TaskState.RUNNING, null, null),
                System.currentTimeMillis());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        progress.writeTo(out);
        out.close();

        ByteArrayInputStream bais =
                new ByteArrayInputStream(baos.toByteArray());
        DataInputStream in = new DataInputStream(bais);
        TaskProgress deserialized = new TaskProgress();
        deserialized.readFrom(in);
        in.close();

        Assert.assertNotNull("Status should exist", deserialized.getStatus());
        Assert.assertEquals("State should match", TaskState.RUNNING,
                deserialized.getStatus().getState());
        Assert.assertNull("Result should be null",
                deserialized.getStatus().getResult());
        Assert.assertTrue("Heartbeat should be recent",
                deserialized.getLastHeartbeat() > 0);
    }
}
