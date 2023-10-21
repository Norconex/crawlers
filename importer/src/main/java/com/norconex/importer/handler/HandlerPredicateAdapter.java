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
package com.norconex.importer.handler;

import static com.norconex.importer.ImporterEvent.IMPORTER_HANDLER_BEGIN;
import static com.norconex.importer.ImporterEvent.IMPORTER_HANDLER_CONDITION_FALSE;
import static com.norconex.importer.ImporterEvent.IMPORTER_HANDLER_CONDITION_TRUE;
import static com.norconex.importer.ImporterEvent.IMPORTER_HANDLER_END;
import static com.norconex.importer.ImporterEvent.IMPORTER_HANDLER_ERROR;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.norconex.commons.lang.flow.FlowPredicateAdapter;
import com.norconex.commons.lang.xml.flow.XMLFlow;
import com.norconex.importer.ImporterEvent;
import com.norconex.importer.handler.condition.Condition;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

/**
 * Predicate wrapping an {@link Condition} instance for use in an
 * {@link XMLFlow}.
 */
@EqualsAndHashCode
@ToString
@FieldNameConstants
public class HandlerPredicateAdapter
        implements FlowPredicateAdapter<HandlerContext> {

    private Condition condition;

    public HandlerPredicateAdapter() {
    }
    public HandlerPredicateAdapter(Condition condition) {
        this.condition = condition;
    }

    @Override
    public Condition getPredicateAdaptee() {
        return condition;
    }
    @Override
    public void setPredicateAdaptee(Object condition) {
        this.condition = (Condition) condition;
    }

    @Override
    public boolean test(HandlerContext ctx) {
        if (condition == null || ctx.isRejected()) {
            return false;
        }

        fireEvent(ctx, IMPORTER_HANDLER_BEGIN);
        try {
            var result = condition.testDocument(
                    new HandlerDoc(ctx.getDoc()),
                    ctx.getDoc().getInputStream(),
                    ctx.getParseState());
            fireEvent(ctx, result
                    ? IMPORTER_HANDLER_CONDITION_TRUE
                    : IMPORTER_HANDLER_CONDITION_FALSE);
            return result;
        } catch (ImporterHandlerException e) {
            fireEvent(ctx, IMPORTER_HANDLER_ERROR, e);
            ExceptionUtils.wrapAndThrow(e);
        } catch (Exception e) {
            fireEvent(ctx, IMPORTER_HANDLER_ERROR, e);
            ExceptionUtils.wrapAndThrow(new ImporterHandlerException(
                    "Importer failure for handler condition: " + condition, e));
        }
        fireEvent(ctx, IMPORTER_HANDLER_END);
        return false;
    }

    private void fireEvent(HandlerContext ctx, String eventName) {
        fireEvent(ctx, eventName, null);
    }
    private void fireEvent(
            HandlerContext ctx, String eventName, Exception e) {
        ctx.getEventManager().fire(
                ImporterEvent.builder()
                    .name(eventName)
                    .source(condition)
                    .document(ctx.getDoc())
                    .parseState(ctx.getParseState())
                    .exception(e)
                    .build());
    }
}
