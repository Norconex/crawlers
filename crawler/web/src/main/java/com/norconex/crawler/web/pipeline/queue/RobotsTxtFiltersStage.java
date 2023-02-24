/* Copyright 2016-2023 Norconex Inc.
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
package com.norconex.crawler.web.pipeline.queue;

import static com.norconex.crawler.web.util.Web.config;

import java.util.function.Predicate;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.crawler.web.crawler.HttpCrawlerEvent;
import com.norconex.crawler.web.robot.RobotsTxtFilter;

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
class RobotsTxtFiltersStage implements Predicate<DocRecordPipelineContext> {

    @Override
    public boolean test(DocRecordPipelineContext ctx) {
        var cfg = config(ctx);

        if (!cfg.isIgnoreRobotsTxt()) {
            var filter = findRejectingRobotsFilter(ctx);
            if (filter != null) {
                ctx.getDocRecord().setState(CrawlDocState.REJECTED);
                ctx.fire(CrawlerEvent.builder()
                        .name(HttpCrawlerEvent.REJECTED_ROBOTS_TXT)
                        .source(ctx.getCrawler())
                        .subject(filter)
                        .crawlDocRecord(ctx.getDocRecord())
                        .build());
                LOG.debug("REJECTED by robots.txt. "
                        + ". Reference={} Filter={}",
                        ctx.getDocRecord().getReference(), filter);
                return false;
            }
        }
        return true;
    }


    /* Find matching rules, knowing that "Allow" work like this:
     * "A matching Allow directive beats a matching Disallow only if it
     * contains more or equal number of characters in the path".
     * (logic described here: http://tools.seobook.com/robots-txt/)
     */
    private RobotsTxtFilter findRejectingRobotsFilter(
            DocRecordPipelineContext ctx) {

        var robotsTxt = HttpQueuePipeline.getRobotsTxt(ctx);
        if (robotsTxt == null) {
            return null;
        }
        var disallowFilters = robotsTxt.getDisallowFilters();
        var allowFilters = robotsTxt.getAllowFilters();
        String url = ctx.getDocRecord().getReference();

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