/* Copyright 2010-2023 Norconex Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.sitemap.SitemapResolutionContext;
import com.norconex.crawler.web.util.Web;

class SitemapResolutionStage implements Predicate<DocRecordPipelineContext> {
    @Override
    public boolean test(DocRecordPipelineContext ctx) { //NOSONAR
        var cfg = Web.config(ctx);
        if (cfg.isIgnoreSitemap()
                || cfg.getSitemapResolver() == null) {
            return true;
        }
        var urlRoot =  ((WebDocRecord) ctx.getDocRecord()).getUrlRoot();
        List<String> robotsTxtLocations = new ArrayList<>();
        var robotsTxt = WebQueuePipeline.getRobotsTxt(ctx);
        if (robotsTxt != null) {
            robotsTxtLocations.addAll(robotsTxt.getSitemapLocations());
        }
        final var sitemapResolver = cfg.getSitemapResolver();

        sitemapResolver.resolveSitemaps(SitemapResolutionContext
                .builder()
                .fetcher((HttpFetcher) ctx.getCrawler().getFetcher())
                .sitemapLocations(robotsTxtLocations)
                .startURLs(false)
                .urlRoot(urlRoot)
                .urlConsumer(rec -> ctx.getCrawler().queueDocRecord(rec))
                .build());
        return true;
    }
}