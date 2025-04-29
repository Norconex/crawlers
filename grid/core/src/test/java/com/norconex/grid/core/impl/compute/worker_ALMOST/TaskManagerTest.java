package com.norconex.grid.core.impl.compute.worker_ALMOST;

import static org.mockito.Mockito.mock;

import org.jgroups.Address;
import org.junit.Before;
import org.junit.jupiter.api.Test;

import com.norconex.grid.core.impl.compute.GridTask;
import com.norconex.grid.core.impl.compute.TaskHandler;
import com.norconex.grid.core.impl.compute.TaskState;
import com.norconex.grid.core.impl.compute.worker_ALMOST.TaskManager;
import com.norconex.grid.core.impl_DELETE.compute.task.TaskProgress;

// Mock Address implementation for testing
class MockAddress implements Address {
    private final String name;

    MockAddress(String name) {
        this.name = name;
    }

    @Override
    public int compareTo(Address o) {
        return name.compareTo(((MockAddress) o).name);
    }

    @Override
    public String toString() {
        return name;
    }
}

// TaskManagerTest
public class TaskManagerTest {
    private TaskManager taskManager;
    private TaskHandler taskHandler;
    private GridTask task;

    @Before
    public void setUp() {
        taskHandler = mock(TaskHandler.class);
        taskManager = new TaskManager("node1");
        task = new GridTask("test_task");
    }

    @Test
    public void testStartTaskAndProgress() throws Exception {
        taskManager.startTask(task, taskHandler);

        // Wait for task to start
        Thread.sleep(100);

        TaskProgress progress = taskManager.getTaskProgressFromNodes(task.getId());
        Assert.assertNotNull("Progress should exist", progress);
        Assert.assertNotNull("Status should exist", progress.getStatus());
        Assert.assertEquals("Task should be RUNNING", TaskState.RUNNING,
                progress.getStatus().getState());
        Assert.assertTrue("Heartbeat should be recent",
                progress.getLastHeartbeat() > 0);
    }

    @Test
    public void testUpdateHeartbeat() throws Exception {
        taskManager.startTask(task, taskHandler);
        Thread.sleep(100);

        long initialHeartbeat = taskManager.getTaskProgressFromNodes(task.getId())
                .getLastHeartbeat();
        taskManager.updateHeartbeat(task.getId());
        long newHeartbeat = taskManager.getTaskProgressFromNodes(task.getId())
                .getLastHeartbeat();

        Assert.assertTrue("Heartbeat should be updated",
                newHeartbeat > initialHeartbeat);
    }

    @Test
    public void testClearTask() {
        taskManager.startTask(task, taskHandler);
        taskManager.clearTask(task.getId());

        TaskProgress progress = taskManager.getTaskProgressFromNodes(task.getId());
        Assert.assertNull("Progress should be cleared", progress.getStatus());
        Assert.assertEquals("Heartbeat should be 0", 0L,
                progress.getLastHeartbeat());
    }
}

// GridWorkerTest
