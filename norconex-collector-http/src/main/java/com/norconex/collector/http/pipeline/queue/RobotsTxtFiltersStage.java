/* Copyright 2016-2018 Norconex Inc.
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
package com.norconex.collector.http.pipeline.queue;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.robot.IRobotsTxtFilter;
import com.norconex.collector.http.robot.RobotsTxt;

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
 * @author Pascal Essiembre
 * @since 2.4.0
 */
/*default*/ class RobotsTxtFiltersStage extends AbstractQueueStage {

    private static final Logger LOG =
            LoggerFactory.getLogger(RobotsTxtFiltersStage.class);

    @Override
    public boolean executeStage(HttpQueuePipelineContext ctx) {
        if (!ctx.getConfig().isIgnoreRobotsTxt()) {
            IRobotsTxtFilter filter = findRejectingRobotsFilter(ctx);
            if (filter != null) {
                ctx.getCrawlData().setState(HttpCrawlState.REJECTED);
                ctx.fireCrawlerEvent(HttpCrawlerEvent.REJECTED_ROBOTS_TXT,
                        ctx.getCrawlData(), filter);
                LOG.debug("REJECTED by robots.txt. "
                        + ". Reference={} Filter={}",
                        ctx.getCrawlData().getReference(), filter);
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
    private IRobotsTxtFilter findRejectingRobotsFilter(
            HttpQueuePipelineContext ctx) {

        RobotsTxt robotsTxt = HttpQueuePipeline.getRobotsTxt(ctx);
        if (robotsTxt == null) {
            return null;
        }
        List<IRobotsTxtFilter> disallowFilters = robotsTxt.getDisallowFilters();
        List<IRobotsTxtFilter> allowFilters = robotsTxt.getAllowFilters();
        String url = ctx.getCrawlData().getReference();

        for (IRobotsTxtFilter df : disallowFilters) {
            if (!df.acceptReference(url)) {
                // Rejected by Disallow, check if overruled by Allow
                boolean overruled = false;
                for (IRobotsTxtFilter af : allowFilters) {
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