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
package com.norconex.crawler.core.commands.crawl.task.pipelines.importer.stages;

import java.util.function.Predicate;

import com.norconex.crawler.core.commands.crawl.task.pipelines.importer.ImporterPipelineContext;
import com.norconex.importer.util.CommonAttributesResolver;

import lombok.RequiredArgsConstructor;

/**
 * <p>
 * Generic pipeline stage for detecting common document attributes, as
 * described in {@link CommonAttributesResolver}.
 * Typically used right after a fetcher stage.
 * Will only perform detection if it was not already performed by the fetcher.
 * Same with setting related metadata properties, they'll be set only
 * if not already set.
 * </p>
 * <p>
 * While the importer will also detect these if not already detected, we do
 * it before import in case those bits of information can be used prior
 * to importing, like in document metadata filters.
 * </p>
 */
@RequiredArgsConstructor
public class CommonAttribsResolutionStage
        implements Predicate<ImporterPipelineContext> {

    @Override
    public boolean test(ImporterPipelineContext ctx) {
        CommonAttributesResolver.resolve(ctx.getDoc());
        return true;
    }
}
