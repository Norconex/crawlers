/* Copyright 2016-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.queue.stages;

import java.util.function.Predicate;

import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.web.doc.operations.robot.RobotsTxtFilter;
import com.norconex.crawler.web.event.WebCrawlerEvent;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Apply robot rules provided by resolved RobotsTxt.
 * </p>
 * <p>
 * The "Allow" directive as defined by
 * <a href="https://support.google.com/webmasters/answer/6062596?hl=en">
 * Google robot rules</a> is also supported (even if not "standard").
 * </p>
 *
 * @since 2.4.0
 */
@Slf4j
public class RobotsTxtFiltersStage implements Predicate<QueuePipelineContext> {

    @Override
    public boolean test(QueuePipelineContext ctx) {
        var cfg = Web.config(ctx.getCrawlContext());

        if (cfg.getRobotsTxtProvider() == null) {
            return true;
        }

        var filter = findRejectingRobotsFilter(ctx);
        if (filter != null) {
            ctx.getDocContext().setState(CrawlDocStatus.REJECTED);
            ctx.getCrawlContext().fire(
                    CrawlerEvent.builder()
                            .name(WebCrawlerEvent.REJECTED_ROBOTS_TXT)
                            .source(ctx.getCrawlContext())
                            .subject(filter)
                            .docContext(ctx.getDocContext())
                            .build());
            LOG.debug("REJECTED by robots.txt. Reference={} Filter={}",
                    ctx.getDocContext().getReference(), filter);
            return false;
        }
        return true;
    }

    /* Find matching rules, knowing that "Allow" work like this:
     * "A matching Allow directive beats a matching Disallow only if it
     * contains more or equal number of characters in the path".
     * (logic described here: http://tools.seobook.com/robots-txt/)
     */
    private RobotsTxtFilter findRejectingRobotsFilter(
            QueuePipelineContext ctx) {

        var robotsTxt = Web.robotsTxt(
                ctx.getCrawlContext(), ctx.getDocContext().getReference());
        if (robotsTxt == null) {
            return null;
        }
        var disallowFilters = robotsTxt.getDisallowFilters();
        var allowFilters = robotsTxt.getAllowFilters();
        String url = ctx.getDocContext().getReference();

        for (RobotsTxtFilter df : disallowFilters) {
            if (!df.acceptReference(url)) {
                // Rejected by Disallow, check if overruled by Allow
                var overruled = false;
                for (RobotsTxtFilter af : allowFilters) {
                    // allow path length must be longer than disallow.
                    if (af.getPath().length() > df.getPath().length()
                            && af.acceptReference(url)) {
                        overruled = true;
                        break;
                    }
                }
                if (!overruled) {
                    return df;
                }
            }
        }
        return null;
    }
}
