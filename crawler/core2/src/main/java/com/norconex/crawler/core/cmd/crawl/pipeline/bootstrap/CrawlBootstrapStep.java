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

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.crawler.core.cluster.pipeline.BaseStep;
import com.norconex.crawler.core2.session.CrawlSession;

/**
 * Bootstrap session artifacts, including the cluster, making it ready for
 * crawling.
 */
public class CrawlBootstrapStep extends BaseStep {

    public CrawlBootstrapStep(String id) {
        super(id);
    }

    @Override
    public void execute(CrawlSession session) {
        var ctx = session.getCrawlContext();

        if (CollectionUtils.isNotEmpty(ctx.getBootstrappers())) {
            ctx.getBootstrappers().forEach(boot -> boot.bootstrap(session));
        }
    }
}
