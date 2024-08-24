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
package com.norconex.crawler.core.doc.pipelines.importer.stages;

import java.util.function.Predicate;

import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.fetch.FetchDirective;

import lombok.Getter;

public abstract class AbstractImporterStage
        implements Predicate<ImporterPipelineContext> {

    @Getter
    private final FetchDirective fetchDirective;

    protected AbstractImporterStage() {
        this(null);
    }
    protected AbstractImporterStage(FetchDirective fetchDirective) {
        this.fetchDirective = fetchDirective;
    }

    @Override
    public final boolean test(ImporterPipelineContext context) {
        return executeStage(context);
    }
    protected abstract boolean executeStage(ImporterPipelineContext ctx);
}
