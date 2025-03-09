/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.grid.impl.ignite;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.JobExecutionContext;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class IgniteGridTaskAdapter implements ComputeJob<String, Object> {
    //    private static final long serialVersionUID = 1L;
    //    private Class<? extends GridTask<T>> taskClass;
    //    private String arg;

    @Override
    public CompletableFuture<Object> executeAsync(
            JobExecutionContext context,
            @NonNull String computeArg) { //NOSONAR
        return CompletableFuture.supplyAsync(() -> {
            try {
                var taskClassStr = computeArg;
                String taskArg = null;
                if (computeArg.contains(":")) {
                    taskClassStr = StringUtils.substringBefore(computeArg, ":");
                    taskArg = StringUtils.substringAfter(computeArg, ":");
                }
                @SuppressWarnings("unchecked")
                var taskClass =
                        (Class<GridTask<?>>) Class.forName(taskClassStr);
                var ctx = CrawlerContext.get(context.ignite().name());
                ctx.fire(CrawlerEvent.TASK_RUN_BEGIN,
                        taskClass.getSimpleName());
                Object value =
                        ClassUtil.newInstance(taskClass).run(ctx, taskArg);
                ctx.fire(CrawlerEvent.TASK_RUN_END, taskClass.getSimpleName());
                return value;
            } catch (Exception e) {
                throw new GridException("Coult not run task with compute arg: "
                        + computeArg
                        + " (rolled back any pending transactions).",
                        e);
            }
        });

        //        var ctx = CrawlerContext.get(context.ignite().name());
        //        ctx.fire(CrawlerEvent.TASK_RUN_BEGIN, taskClass.getSimpleName());
        //        ClassUtil.newInstance(taskClass).run(ctx, arg);
        //        ctx.fire(CrawlerEvent.TASK_RUN_END, taskClass.getSimpleName());
        //        return null;
        //        //return response;
    }

    static String toComputeArg(
            @NonNull Class<? extends GridTask<?>> taskClass, String taskArg) {
        var arg = taskClass.getName();
        if (taskArg != null) {
            arg += ":" + taskArg;
        }
        return arg;
    }
    //    @Override
    //    public T call() throws Exception {
    //        var ctx = CrawlerContext.get(
    //                Ignition.localIgnite().cluster().localNode().id().toString());
    //        ctx.fire(CrawlerEvent.TASK_RUN_BEGIN, taskClass.getSimpleName());
    //        var response = ClassUtil.newInstance(taskClass).run(ctx, arg);
    //        ctx.fire(CrawlerEvent.TASK_RUN_END, taskClass.getSimpleName());
    //        return response;
    //    }
}
