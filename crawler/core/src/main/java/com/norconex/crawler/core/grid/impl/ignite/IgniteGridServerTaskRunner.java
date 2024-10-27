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
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.grid.GridTask;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class IgniteGridServerTaskRunner {

    private IgniteGridServerTaskRunner() {
    }

    // Static method that gets the Ignite instance internally
    public static void execute(String className, String arg) {

        // Load the task class dynamically
        var taskClass = classFor(className);

        // Create a new instance of the task
        var task = (GridTask) ClassUtil.newInstance(taskClass);

        try (var crawlerContext = createCrawlerContext()) {
            LOG.info("Running task \"{}\" on crawler \"{}\"",
                    className, crawlerContext.getConfiguration().getId());
            crawlerContext.init();
            crawlerContext.fire(CrawlerEvent.TASK_RUN_BEGIN, className);
            task.run(crawlerContext, arg);
            crawlerContext.fire(CrawlerEvent.TASK_RUN_END, className);
        } catch (RuntimeException e) {
            LOG.error("Could not run task.", e);
            throw e;
        }
    }

    static CrawlerContext createCrawlerContext() {
        var ignite = Ignition.localIgnite();
        var initCache = ignite.<String, String>getOrCreateCache(
                IgniteGridKeys.GLOBAL_CACHE);

        var specProviderClass = specProviderClassFromIgnite(initCache);
        var spec = ClassUtil.newInstance(
                specProviderClass).get();
        var cfg = ClassUtil.newInstance(spec.crawlerConfigClass());

        var configStr = initCache.get(IgniteGridKeys.CRAWLER_CONFIG);
        if (StringUtils.isBlank(configStr)) {
            throw new GridException("""
                Crawler on grid server node not initialized: could not find
                crawler configuration in Ignite cache.""");
        }

        var r = new StringReader(configStr);
        spec.beanMapper().read(cfg, r, Format.JSON);
        ((IgniteGridConnector) cfg.getGridConnector()).setServerNode(true);
        return new CrawlerContext(specProviderClass, cfg);
    }

    private static Class<? extends CrawlerSpecProvider>
            specProviderClassFromIgnite(
                    IgniteCache<String, String> initCache) {
        var className = initCache
                .get(IgniteGridKeys.CRAWLER_SPEC_PROVIDER_CLASS);
        if (StringUtils.isBlank(className)) {
            throw new GridException("""
                Crawler on grid server node not initialized. \
                Could not find class name in global Ignite cache under key: %s\
                """.formatted(IgniteGridKeys.CRAWLER_SPEC_PROVIDER_CLASS));
        }
        return classFor(className);
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
