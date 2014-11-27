/* Copyright 2010-2014 Norconex Inc.
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
package com.norconex.collector.http.pipeline.queue;

import com.norconex.collector.core.pipeline.BasePipelineContext;
import com.norconex.commons.lang.pipeline.IPipelineStage;

/**
 * @author Pascal Essiembre
 *
 */
/*default*/ abstract class AbstractQueueStage 
        implements IPipelineStage<BasePipelineContext> {
    @Override
    public final boolean execute(BasePipelineContext context) {
        if (!(context instanceof HttpQueuePipelineContext)) {
            throw new AssertionError("Unexpected type: " + context);
        }
        return executeStage((HttpQueuePipelineContext) context);
    }
    public abstract boolean executeStage(HttpQueuePipelineContext ctx);
}
