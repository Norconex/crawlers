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

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class IgniteGridServerTaskRunner {

    private IgniteGridServerTaskRunner() {
    }

    // Static method that gets the Ignite instance internally
    public static <T> T execute(String className, String arg) {

        // Load the task class dynamically
        var taskClass = classFor(className);

        // Create a new instance of the task
        @SuppressWarnings("unchecked")
        var task = (GridTask<T>) ClassUtil.newInstance(taskClass);

        try {
            var crawlerContext = getCrawlerContext();
            LOG.info("Running task \"{}\" on crawler \"{}\"",
                    className, crawlerContext.getConfiguration().getId());
            crawlerContext.fire(CrawlerEvent.TASK_RUN_BEGIN, className);
            var result = task.run(crawlerContext, arg);
            crawlerContext.fire(CrawlerEvent.TASK_RUN_END, className);
            return result;
        } catch (RuntimeException e) {
            LOG.error("Could not run task '%s'.".formatted(className), e);
            throw e;
        }
    }

    static CrawlerContext getCrawlerContext() {
        var ignite = Ignition.localIgnite();
        var contextService = ignite.services().serviceProxy(
                IgniteGridKeys.CONTEXT_SERVICE,
                IgniteGridCrawlerContextService.class,
                false);
        return contextService.getContext();
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> classFor(String className) {
        try {
            return (Class<T>) Class.forName(className, true,
                    Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new GridException("Could not initialize class: "
                    + className, e);
        }
    }
}
