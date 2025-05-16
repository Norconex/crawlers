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
package com.norconex.crawler.core.cmd.clean;

import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.grid.core.compute.GridTaskBuilder;
import com.norconex.grid.core.compute.TaskState;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanCommand implements Command {

    @Override
    public void execute(CrawlContext ctx) {
        Thread.currentThread().setName(ctx.getId() + "/CLEAN");
        ctx.fire(CrawlerEvent.CRAWLER_CLEAN_BEGIN);
        var result = ctx.getGrid()
                .getCompute()
                .executeTask(GridTaskBuilder.create("cleanTask")
                        .singleNode()
                        .processor(grid -> {
                            var cntx = CrawlContext.get(grid);
                            cntx.getCommitterService().clean();
                            // Close metrics prematurely, before cleaning, or
                            // it will want to report on a blown-away store:
                            cntx.getMetrics().close();
                            cntx.getGrid().getStorage().destroy();
                        })
                        .build());

        if (result.getState() != TaskState.COMPLETED) {
            LOG.warn("Command returned with a non-completed status: {}",
                    result);
        } else {
            LOG.info("Clean command executed.");
        }
        ctx.fire(CrawlerEvent.CRAWLER_CLEAN_END);
    }
}
