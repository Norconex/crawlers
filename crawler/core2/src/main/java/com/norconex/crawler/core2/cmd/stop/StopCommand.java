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
package com.norconex.crawler.core2.cmd.stop;

import com.norconex.crawler.core2.cmd.Command;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StopCommand implements Command {

    @Override
    public void execute(CrawlSession session) {
        var ctx = session.getCrawlContext();
        Thread.currentThread().setName(ctx.getId() + "/STOP");
        session.fire(CrawlerEvent.CRAWLER_STOP_REQUEST_BEGIN, this);

        session.getCluster().stop();
        // we don't close, it will close on its own when fully stopped

        LOG.info("Stop command executed.");

        session.fire(CrawlerEvent.CRAWLER_STOP_REQUEST_END, this);
    }
}
