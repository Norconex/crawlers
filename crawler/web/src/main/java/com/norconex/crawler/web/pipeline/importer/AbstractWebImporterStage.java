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
package com.norconex.crawler.web.pipeline.importer;

import java.util.Objects;
import java.util.function.Predicate;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.util.Web;

/**
 * Base implementation for Web pipeline stages, offering typed-arguments
 * and optional HTTP method for when a stage supports more than one.
 */
abstract class AbstractWebImporterStage
        implements Predicate<ImporterPipelineContext> {

    private final HttpMethod method;
    protected AbstractWebImporterStage() {
        method = null;
    }
    protected AbstractWebImporterStage(HttpMethod method) {
        this.method = Objects.requireNonNull(
                method, "'method' must not be null.");
        if (EqualsUtil.equalsNone(method, HttpMethod.HEAD, HttpMethod.GET)) {
            throw new IllegalArgumentException("Unsupported method: " + method);
        }
    }

    HttpMethod getHttpMethod() {
        return method;
    }

    @Override
    public final boolean test(ImporterPipelineContext ctx) {
        if (!(ctx instanceof WebImporterPipelineContext)) {
            throw new AssertionError("Unexpected type: " + ctx.getClass());
        }
        return executeStage(Web.context(ctx));
    }

    abstract boolean executeStage(WebImporterPipelineContext ctx);
}
