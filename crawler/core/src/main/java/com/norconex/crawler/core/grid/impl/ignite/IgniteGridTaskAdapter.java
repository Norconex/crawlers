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

import org.apache.ignite.Ignition;
import org.apache.ignite.lang.IgniteCallable;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridTask;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
class IgniteGridTaskAdapter<T> implements IgniteCallable<T> {
    private static final long serialVersionUID = 1L;
    private final Class<? extends GridTask<T>> taskClass;
    private final String arg;

    @Override
    public T call() throws Exception {
        var ctx = CrawlerContext.get(
                Ignition.localIgnite().cluster().localNode().id().toString());
        ctx.fire(CrawlerEvent.TASK_RUN_BEGIN, taskClass.getSimpleName());
        var response = ClassUtil.newInstance(taskClass).run(ctx, arg);
        ctx.fire(CrawlerEvent.TASK_RUN_END, taskClass.getSimpleName());
        return response;
    }
}