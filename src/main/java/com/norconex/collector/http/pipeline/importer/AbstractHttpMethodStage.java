/* Copyright 2020-2021 Norconex Inc.
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
package com.norconex.collector.http.pipeline.importer;

import java.util.Objects;

import com.norconex.collector.http.crawler.HttpCrawlerConfig.HttpMethodSupport;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.commons.lang.EqualsUtil;

/**
 * For stages specific to an HTTP method.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
/*default*/ abstract class AbstractHttpMethodStage
        extends AbstractImporterStage {
    private final HttpMethod method;
    protected AbstractHttpMethodStage(HttpMethod method) {
        super();
        this.method = Objects.requireNonNull(
                method, "'method' must not be null.");
        if (EqualsUtil.equalsNone(method, HttpMethod.HEAD, HttpMethod.GET)) {
            throw new IllegalArgumentException("Unsupported method: " + method);
        }
    }
    @Override
    public final boolean executeStage(HttpImporterPipelineContext ctx) {
        // If stage is for a method that was disabled, skip
        if (!isMethodEnabled(method, ctx)) {
            return true;
        }
        return executeStage(ctx, method);
    }
    /**
     * Execute this stage. Is only invoked if appropriate. For instance,
     * if a separate HTTP HEAD request was NOT required to be performed,
     * this method will never get invoked for a HEAD method.
     * @param ctx pipeline context
     * @param method HTTP method
     * @return <code>true</code> if we continue processing.
     */
    public abstract boolean executeStage(
            HttpImporterPipelineContext ctx, HttpMethod method);

    /**
     * Whether a separate HTTP HEAD request was requested (configured)
     * and was performed already.
     * @param ctx pipeline context
     * @return <code>true</code> if method is GET and HTTP HEAD was performed
     */
    protected boolean wasHttpHeadPerformed(HttpImporterPipelineContext ctx) {
        // If GET and fetching HEAD was requested, we ran filters already, skip.
        return method == HttpMethod.GET
                &&  HttpMethodSupport.isEnabled(
                        ctx.getConfig().getFetchHttpHead());
    }

    private boolean isMethodEnabled(
            HttpMethod method,  HttpImporterPipelineContext ctx) {
        return (method == HttpMethod.HEAD
                && HttpMethodSupport.isEnabled(
                        ctx.getConfig().getFetchHttpHead()))
                || (method == HttpMethod.GET
                        && HttpMethodSupport.isEnabled(
                                ctx.getConfig().getFetchHttpGet()));
    }
}
