/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines.importer;

import java.util.function.Function;

import com.norconex.commons.lang.function.Predicates;
import com.norconex.importer.response.ImporterResponse;

import lombok.Builder;

public class ImporterPipeline
        implements Function<ImporterPipelineContext, ImporterResponse> {

    private final Predicates<ImporterPipelineContext> stages;

    @Builder
    private ImporterPipeline(Predicates<ImporterPipelineContext> stages) {
        this.stages = stages;
    }

    @Override
    public ImporterResponse apply(ImporterPipelineContext ctx) {
        stages.test(ctx);
        return ctx.getImporterResponse();
    }
}
