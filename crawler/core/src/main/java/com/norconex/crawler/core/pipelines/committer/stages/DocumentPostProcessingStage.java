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
package com.norconex.crawler.core.pipelines.committer.stages;

import java.util.function.Predicate;

import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.operations.DocumentConsumer;
import com.norconex.crawler.core.pipelines.committer.CommitterPipelineContext;

public class DocumentPostProcessingStage
        implements Predicate<CommitterPipelineContext> {

    @Override
    public boolean test(CommitterPipelineContext ctx) {
        for (DocumentConsumer postProc : ctx.getCrawlerContext()
                .getConfiguration()
                .getPostImportConsumers()) {
            postProc.accept(ctx.getCrawlerContext().getFetcher(), ctx.getDoc());
            ctx.getCrawlerContext().fire(
                    CrawlerEvent.builder()
                            .name(CrawlerEvent.DOCUMENT_POSTIMPORTED)
                            .source(ctx.getCrawlerContext())
                            .subject(postProc)
                            .docContext(ctx.getDoc().getDocContext())
                            .build());
        }
        return true;
    }
}
