/* Copyright 2024-2025 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.grid.core.impl.compute;

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.grid.core.GridException;
import com.norconex.grid.core.compute.GridCompute;
import com.norconex.grid.core.compute.GridPipeline;
import com.norconex.grid.core.compute.GridTask;
import com.norconex.grid.core.impl.CoreGrid;
import com.norconex.grid.core.impl.compute.work.WorkCoordinator;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CoreCompute implements GridCompute {

    @Getter
    private final CoreGrid grid;

    private final WorkCoordinator coord;

    //    private final CorePipelineExecutor pipelineExecutor;
    //    @Getter
    //    private final CoreTaskExecutor taskExecutor;
    //    @Getter
    //    private final CoreWorker worker;
    //    @Getter
    //    private final RpcDispatcher dispatcher;

    public CoreCompute(CoreGrid grid) {
        this.grid = grid;
        coord = new WorkCoordinator(grid);

        //        worker = new CoreWorker(grid);
        //        dispatcher = new RpcDispatcher(grid.getChannel(), worker);
        //        taskExecutor = new CoreTaskExecutor(this, dispatcher);
        //        pipelineExecutor = new CorePipelineExecutor(this);
    }

    @Override
    public TaskStatus executeTask(@NonNull GridTask task) {
        try {
            var status = coord.executeTask(task);
            if (status == null || status.getState() == null) {
                return new TaskStatus(TaskState.FAILED, null,
                        "Could not determine state of executed task.");
            }
            return status;
        } catch (Exception e) {
            LOG.error("Exception occured executing task {}", task.getId(), e);
            return new TaskStatus(TaskState.FAILED, null,
                    "Exception occured executing task " + task.getId() + ": "
                            + ExceptionUtil.getFormattedMessages(e));
        }
    }

    @Override
    public void executePipeline(@NonNull GridPipeline pipeline) {
        System.err.println("XXX HELLO Pipeline!");
        //        try {
        //            pipelineExecutor.execute(pipeline);
        //        } catch (Exception e) {
        //            // TODO Auto-generated catch block
        //            e.printStackTrace();
        //        }
    }

    @Override
    public void stopTask(String taskId) {
        try {
            coord.stopTask(taskId);
        } catch (Exception e) {
            throw new GridException(
                    "Error while requesting task to stop: " + taskId);
        }
    }

    //
    //    if (dispatcher != null)
    //        try {
    //            dispatcher.close();
    //        } catch (IOException e) {
    //            // TODO Auto-generated catch block
    //            e.printStackTrace();
    //        }
}
