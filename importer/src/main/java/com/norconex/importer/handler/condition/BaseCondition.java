/* Copyright 2021-2023 Norconex Inc.
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
package com.norconex.importer.handler.condition;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.norconex.importer.handler.DocContext;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * An abstract base class for a condition, wrapping {@link IOException}
 * into {@link UncheckedIOException}.
 */
@ToString
@EqualsAndHashCode
public abstract class BaseCondition implements Condition {

    @Override
    public final boolean test(DocContext ctx) {
        // Fire events?
//        fireEvent(ctx, ImporterEvent.IMPORTER_HANDLER_BEGIN);
        try {
            ctx.condition(this);
            return evaluate(ctx);
        } catch (IOException e) {
//            fireEvent(ctx, ImporterEvent.IMPORTER_HANDLER_ERROR, e);
            throw new UncheckedIOException(
                    "Importer failure for handler: " + this, e);
        }
//        fireEvent(ctx, ImporterEvent.IMPORTER_HANDLER_END);
    }

    protected abstract boolean evaluate(DocContext docCtx) throws IOException;



    //TODO needed?
    //TODO extend Predicate and replace method or have a default one?
    // or eliminate in favor of predicate?

}
