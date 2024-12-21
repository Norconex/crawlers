/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.pipelines.importer.stages;

import com.norconex.crawler.core.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.pipelines.importer.stages.AbstractImporterStage;
import com.norconex.crawler.web.util.Web;

/**
 * Wait for configured or default delay to expire.
 */
public class DelayResolverStage extends AbstractImporterStage {
    @Override
    protected boolean executeStage(ImporterPipelineContext ctx) {
        var delayResolver =
                Web.config(ctx.getCrawlerContext()).getDelayResolver();
        if (delayResolver != null) {
            String reference = ctx.getDoc().getDocContext().getReference();
            delayResolver.delay(
                    Web.robotsTxt(ctx.getCrawlerContext(), reference),
                    reference);
        }
        return true;
    }
}
