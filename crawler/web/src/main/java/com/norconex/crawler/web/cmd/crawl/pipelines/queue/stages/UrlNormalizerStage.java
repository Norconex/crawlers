/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.cmd.crawl.pipelines.queue.stages;

import java.util.function.Predicate;

import com.google.common.base.Objects;
import com.norconex.crawler.core.cmd.crawl.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.web.cmd.crawl.operations.url.WebUrlNormalizer;
import com.norconex.crawler.web.util.Web;

public class UrlNormalizerStage implements Predicate<QueuePipelineContext> {
    @Override
    public boolean test(QueuePipelineContext ctx) {
        var normalizers =
                Web.config(ctx.getCrawlerContext()).getUrlNormalizers();
        if (!normalizers.isEmpty()) {
            String originalRef = ctx.getDocContext().getReference();
            var url = WebUrlNormalizer.normalizeURL(originalRef, normalizers);
            if (url == null) {
                ctx.getDocContext().setState(DocResolutionStatus.REJECTED);
                return false;
            }
            if (!Objects.equal(originalRef, url)) {
                ctx.getDocContext().setReference(url);
                ctx.getDocContext().setOriginalReference(originalRef);
            }
        }
        return true;
    }
}