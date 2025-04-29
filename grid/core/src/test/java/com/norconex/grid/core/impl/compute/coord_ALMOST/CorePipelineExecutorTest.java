package com.norconex.grid.core.impl.compute.coord_ALMOST;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.jgroups.blocks.RequestOptions;
import org.jgroups.util.Rsp;
import org.jgroups.util.RspList;
import org.junit.jupiter.api.Test;

import com.norconex.grid.core.Grid;
import com.norconex.grid.core.impl.compute.Dispatcher;
import com.norconex.grid.core.impl.compute.GridPipeline;
import com.norconex.grid.core.impl.compute.GridTask;
import com.norconex.grid.core.impl.compute.Stage;
import com.norconex.grid.core.impl.compute.TaskState;
import com.norconex.grid.core.impl.compute.TaskStatus;
import com.norconex.grid.core.impl.compute.coord_ALMOST.CorePipelineExecutor;
import com.norconex.grid.core.impl.compute.worker.MockAddress;
import com.norconex.grid.core.impl_DELETE.compute.task.TaskProgress;

public class CorePipelineExecutorTest {
    private CorePipelineExecutor pipelineExecutor;
    private Grid grid;
    private Dispatcher dispatcher;

    @Before
    public void setUp() {
        grid = mock(Grid.class);
        dispatcher = mock(Dispatcher.class);
        when(grid.isCoordinator()).thenReturn(true);
        when(grid.getNodeAddress()).thenReturn("coordinator");
        when(grid.getClusterMembers()).thenReturn(
                Collections.singletonList(new MockAddress("node1")));

        pipelineExecutor = new CorePipelineExecutor(grid, dispatcher);
    }

    @Test
    public void testExecutePipeline() throws Exception {
        GridTask task1 = new GridTask("task1");
        GridTask task2 = new GridTask("task2");
        Stage stage1 = new Stage();
        stage1.setTask(task1);
        Stage stage2 = new Stage();
        stage2.setTask(task2);
        GridPipeline pipeline = new GridPipeline();
        pipeline.setStages(Arrays.asList(stage1, stage2));

        RspList<TaskProgress> progressList = new RspList<>();
        progressList.put(new MockAddress("node1"), new Rsp<>(new TaskProgress(
                new TaskStatus(TaskState.COMPLETED, "Result from node1", null),
                System.currentTimeMillis())));

        RspList<Integer> stageList = new RspList<>();
        stageList.put(new MockAddress("node1"), new Rsp<>(null));

        when(dispatcher.callRemoteMethods(
                anyList(),
                argThat(call -> call.getMethodName().equals("getTaskProgress")),
                any(RequestOptions.class))).thenReturn(progressList);

        when(dispatcher.callRemoteMethods(
                anyList(),
                argThat(call -> call.getMethodName()
                        .equals("getCompletedStage")),
                any(RequestOptions.class))).thenReturn(stageList);

        pipelineExecutor.execute(pipeline);

        String partialResults1 = pipelineExecutor.coordinator.getTaskManager()
                .getPartialResults(task1.getId());
        String partialResults2 = pipelineExecutor.coordinator.getTaskManager()
                .getPartialResults(task2.getId());
        Assert.assertNotNull("Task1 results should exist", partialResults1);
        Assert.assertNotNull("Task2 results should exist", partialResults2);
        Assert.assertTrue("Task1 results should contain node1",
                partialResults1.contains("node1: Result from node1"));
        Assert.assertTrue("Task2 results should contain node1",
                partialResults2.contains("node1: Result from node1"));
    }

    @Test
    public void testSkipCompletedStage() throws Exception {
        GridTask task = new GridTask("task1");
        Stage stage = new Stage();
        stage.setTask(task);
        GridPipeline pipeline = new GridPipeline();
        pipeline.setStages(Collections.singletonList(stage));

        RspList<Integer> stageList = new RspList<>();
        stageList.put(new MockAddress("node1"), new Rsp<>(1)); // Stage 1 completed

        when(dispatcher.callRemoteMethods(
                anyList(),
                argThat(call -> call.getMethodName()
                        .equals("getCompletedStage")),
                any(RequestOptions.class))).thenReturn(stageList);

        pipelineExecutor.execute(pipeline);

        String partialResults = pipelineExecutor.coordinator.getTaskManager()
                .getPartialResults(task.getId());
        Assert.assertNull("No results should be stored for skipped stage",
                partialResults);
    }
}

// TaskStatusSerializationTest
