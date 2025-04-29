package com.norconex.grid.core.impl.compute.worker_ALMOST;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.impl.compute.GridTask;
import com.norconex.grid.core.impl.compute.TaskHandler;
import com.norconex.grid.core.impl.compute.TaskState;
import com.norconex.grid.core.impl.compute.worker_ALMOST.CoreWorker;
import com.norconex.grid.core.impl_DELETE.compute.task.TaskProgress;

public class CoreWorkerTest {
    private CoreWorker worker;
    private Grid grid;
    private TaskHandler taskHandler;

    @Before
    public void setUp() {
        grid = mock(Grid.class);
        taskHandler = mock(TaskHandler.class);
        when(grid.getNodeAddress()).thenReturn("node1");
        worker = new CoreWorker(grid, taskHandler);
    }

    @Test
    public void testExecuteTask() throws Exception {
        GridTask task = new GridTask("test_task");
        worker.executeTask(task);

        Thread.sleep(100);

        TaskProgress progress = worker.getTaskProgress(task.getId());
        Assert.assertNotNull("Progress should exist", progress);
        Assert.assertEquals("Task should be RUNNING", TaskState.RUNNING,
                progress.getStatus().getState());
    }

    @Test
    public void testGetTaskProgress() {
        GridTask task = new GridTask("test_task");
        worker.executeTask(task);

        TaskProgress progress = worker.getTaskProgress(task.getId());
        Assert.assertNotNull("Progress should exist", progress);
        Assert.assertNotNull("Status should exist", progress.getStatus());
    }
}

// GridCoordinatorTest
