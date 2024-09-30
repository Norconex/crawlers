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
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerBuilderFactory;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class IgniteServerTaskExecutor {

    private IgniteServerTaskExecutor() {
    }

    // Static method that gets the Ignite instance internally
    public static void execute(String className, String taskName) {

        // Load the task class dynamically
        Class<?> taskClass;
        try {
            taskClass = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new GridException(
                    "Could not find task class: " + className, e);
        }

        System.err.println("XXXXXXXXX INSTANTIATING: '" + taskClass + "'.");
        // Create a new instance of the task
        var task = (GridTask) ClassUtil.newInstance(taskClass);

        try {
            var crawler = restoreCrawlerLocal();
            task.run(crawler, taskName);
        } catch (RuntimeException e) {
            LOG.error("Could not run task on node.", e);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    static Crawler restoreCrawlerLocal() {
        var ignite = Ignition.localIgnite();

        var initCache = ignite.<String, String>getOrCreateCache(
                IgniteGridSystem.KEY_GLOBAL_CACHE);

        var fqClassName = initCache
                .get(IgniteGridSystem.KEY_CRAWLER_BUILDER_FACTORY_CLASS);
        if (StringUtils.isBlank(fqClassName)) {
            throw new GridException("Grid not initialized: could not find "
                    + "class implementing CrawlerBuilderFactory.");
        }
        Class<?> factoryClass;
        try {
            factoryClass = Class.forName(fqClassName, true,
                    Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new GridException("Could not obtain class: " + fqClassName,
                    e);
        }

        var configStr = initCache.get(IgniteGridSystem.KEY_CRAWLER_CONFIG);
        return Crawler.create(
                (Class<? extends CrawlerBuilderFactory>) factoryClass, b -> {
                    if (StringUtils.isNotBlank(configStr)) {
                        var r = new StringReader(configStr);
                        BeanMapper.DEFAULT.read(b.configuration(), r,
                                Format.JSON);
                    }
                });
    }

}
