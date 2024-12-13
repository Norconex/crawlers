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
package com.norconex.crawler.core.cmd.stop;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.event.CrawlerEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StopCommand implements Command {

    @Override
    public void execute(CrawlerContext ctx) {
        Thread.currentThread().setName(ctx.getId() + "/STOP_REQUEST");
        ctx.fire(CrawlerEvent.CRAWLER_STOP_REQUEST_BEGIN);
        ctx.stop();
        ctx.fire(CrawlerEvent.CRAWLER_STOP_REQUEST_END);
    }
}
