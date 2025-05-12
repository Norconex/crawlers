/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap;

import java.io.Serializable;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.BaseGridTask.SingleNodeTask;

public class CrawlBootstrapTask extends SingleNodeTask {

    private static final long serialVersionUID = 1L;

    public CrawlBootstrapTask(String id) {
        super(id);
    }

    @Override
    public Serializable execute(Grid grid) {
        var ctx = CrawlContext.get(grid);

        // We launch a "run thread" notification here as some crawl related
        // actions can be/are done within an initializer execution (like fetch
        // a sitemap.xml, etc.) and they need to receive this notification.
        ctx.fire(CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN,
                Thread.currentThread());
        try {
            if (CollectionUtils.isNotEmpty(ctx.getBootstrappers())) {
                ctx.getBootstrappers().forEach(boot -> boot.bootstrap(ctx));
            }
        } finally {
            ctx.fire(CrawlerEvent.CRAWLER_RUN_THREAD_END,
                    Thread.currentThread());
        }
        return null;
    }
}
