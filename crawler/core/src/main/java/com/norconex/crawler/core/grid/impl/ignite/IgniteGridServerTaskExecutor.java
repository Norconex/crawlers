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

import java.io.StringReader;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.Ignition;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlerBuilderFactory;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;
import com.norconex.crawler.core.tasks.CrawlerTaskContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class IgniteGridServerTaskExecutor {

    private IgniteGridServerTaskExecutor() {
    }

    // Static method that gets the Ignite instance internally
    public static void execute(String className, String arg) {

        // Load the task class dynamically
        Class<?> taskClass = classFor(className);

        // Create a new instance of the task
        var task = (GridTask) ClassUtil.newInstance(taskClass);

        try {
            task.run(serverNodeCrawler(), arg);
        } catch (RuntimeException e) {
            LOG.error("Could not run task on node.", e);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    static CrawlerTaskContext serverNodeCrawler() {
        var ignite = Ignition.localIgnite();

        var initCache = ignite.<String, String>getOrCreateCache(
                IgniteGridKeys.GLOBAL_CACHE);

        var crawlerBuilderFactoryClassName = initCache
                .get(IgniteGridKeys.CRAWLER_BUILDER_FACTORY_CLASS);
        if (StringUtils.isBlank(crawlerBuilderFactoryClassName)) {
            throw new GridException("""
                Crawler on grid server node not initialized: \
                could not find class implementing CrawlerBuilderFactory \
                in Ignite cache.""");
        }
        Class<?> factoryClass = classFor(crawlerBuilderFactoryClassName);

        var configStr = initCache.get(IgniteGridKeys.CRAWLER_CONFIG);
        return CrawlerTaskContext.create(
                (Class<? extends CrawlerBuilderFactory>) factoryClass, b -> {
                    if (StringUtils.isBlank(configStr)) {
                        throw new GridException("""
                            "Crawler on grid server node not initialized: \
                            could not find crawler configuration in \
                            Ignite cache.""");
                    }
                    var r = new StringReader(configStr);
                    b.beanMapper().read(b.configuration(), r, Format.JSON);
                    LOG.info("Running task \"{}\" on crawler \"{}\"",
                            crawlerBuilderFactoryClassName,
                            b.configuration().getId());
                });
    }

    private static Class<?> classFor(String className) {
        try {
            return Class.forName(className, true,
                    Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new GridException("Could not initialize class: "
                    + className, e);
        }
    }
}
