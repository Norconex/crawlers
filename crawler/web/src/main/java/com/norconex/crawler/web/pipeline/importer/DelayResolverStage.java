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
package com.norconex.crawler.web.pipeline.importer;

import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.util.Web;

/**
 * Wait for configured or default delay to expire.
 */
class DelayResolverStage extends AbstractWebImporterStage {
    @Override
    boolean executeStage(WebImporterPipelineContext ctx) {
        var delayResolver = Web.config(ctx).getDelayResolver();
        if (delayResolver != null) {
            if (!Web.config(ctx).isIgnoreRobotsTxt()) {
                delayResolver.delay(
                        Web.config(ctx).getRobotsTxtProvider().getRobotsTxt(
                                (HttpFetcher) ctx.getCrawler().getFetcher(),
                                ctx.getDocRecord().getReference()),
                        ctx.getDocRecord().getReference());
            } else {
                delayResolver.delay(
                        null, ctx.getDocRecord().getReference());
            }
        }
        return true;
    }
}