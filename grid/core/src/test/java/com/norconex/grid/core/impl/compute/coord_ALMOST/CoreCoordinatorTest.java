package com.norconex.grid.core.impl.compute.coord_ALMOST;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jgroups.Address;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;
import org.junit.jupiter.api.Test;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.impl.compute.Dispatcher;
import com.norconex.grid.core.impl.compute.GridTask;
import com.norconex.grid.core.impl.compute.TaskState;
import com.norconex.grid.core.impl.compute.coord_ALMOST.CoreCoordinator;
import com.norconex.grid.core.impl.compute.worker.MockAddress;
import com.norconex.grid.core.impl_DELETE.compute.task.TaskProgress;

public class CoreCoordinatorTest {
    private CoreCoordinator coordinator;
    private Grid grid;
    private Dispatcher dispatcher;
    private Address node1;
    private Address node2;

    @Before
    public void setUp() {
        grid = mock(Grid.class);
        dispatcher = mock(Dispatcher.class);
        node1 = new MockAddress("node1");
        node2 = new MockAddress("node2");

        when(grid.isCoordinator()).thenReturn(true);
        when(grid.getNodeAddress()).thenReturn("coordinator");
        when(grid.getClusterMembers()).thenReturn(Arrays.asList(node1, node2));

        coordinator = new CoreCoordinator(grid, dispatcher);
    }

    @Test
    public void testExecuteAdHocTask() throws Exception {
        GridTask task = new GridTask("test_task");
        RspList<TaskProgress> progressList = new RspList<>();
        progressList.put(node1, new Rsp<>(new TaskProgress(
                new TaskStatus(TaskState.COMPLETED, "Result from node1", null),
                System.currentTimeMillis())));
        progressList.put(node2, new Rsp<>(new TaskProgress(
                new TaskStatus(TaskState.COMPLETED, "Result from node2", null),
                System.currentTimeMillis())));

        when(dispatcher.callRemoteMethods(
                anyList(),
                any(MethodCall.class),
                any(RequestOptions.class))).thenReturn(progressList);

        coordinator.executeTask(task, false);

        // Verify partial results were stored
        String partialResults = coordinator.getTaskManager()
                .getPartialResults(task.getId());
        Assert.assertNotNull("Partial results should exist", partialResults);
        Assert.assertTrue("Results should contain node1",
                partialResults.contains("node1: Result from node1"));
        Assert.assertTrue("Results should contain node2",
                partialResults.contains("node2: Result from node2"));
    }

    @Test
    public void testExecuteAdHocTaskWithTimeout() throws Exception {
        GridTask task = new GridTask("test_task");
        RspList<TaskProgress> progressList = new RspList<>();
        progressList.put(node1, new Rsp<>(new TaskProgress(
                new TaskStatus(TaskState.RUNNING, null, null),
                System.currentTimeMillis())));
        progressList.put(node2,
                new Rsp<>(null, new Exception("Node unreachable")));

        when(dispatcher.callRemoteMethods(
                anyList(),
                any(MethodCall.class),
                any(RequestOptions.class))).thenReturn(progressList);

        coordinator.executeTask(task, false);

        String partialResults = coordinator.getTaskManager()
                .getPartialResults(task.getId());
        Assert.assertTrue("Results should indicate timeout",
                partialResults.contains("Timed out"));
        Assert.assertTrue("Results should indicate error",
                partialResults.contains("Node unreachable"));
    }
}

// PipelineExecutorTest
