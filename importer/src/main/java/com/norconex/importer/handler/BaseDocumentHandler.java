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
package com.norconex.importer.handler;

import java.io.IOException;
import java.util.function.Consumer;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.function.FailableConsumer;

import com.norconex.importer.ImporterEvent;

import lombok.Data;
import lombok.NonNull;

/**
 * Base class or wrapper class that makes pre/post parse consumers integrate
 * smoother with the importer process. It will fire events before and after
 * an handler is invoked, and handle returned exceptions.
 * While not mandatory, it is recommended to extend this class or wrap your
 * existing consumer instance with it.
 */
public abstract class BaseDocumentHandler implements Consumer<DocContext> {

    @Override
    public final void accept(DocContext ctx) {
        fireEvent(ctx, ImporterEvent.IMPORTER_HANDLER_BEGIN);
        try {
//            if (handler instanceof DocumentTagger t) {
//                tagDocument(ctx, t);
//            } else if (handler instanceof DocumentTransformer t) {
//                transformDocument(ctx, t);
//            } else if (handler instanceof DocumentSplitter s) {
//                splitDocument(ctx, s);
//            } else if (handler instanceof DocumentFilter f) {
//                acceptDocument(ctx, f);
//            } else {
//                //TODO instead check if implementing right consumer
//                // and invoke if so?
//                LOG.error("Unsupported Import Handler: {}", handler);
//            }
            handle(ctx);
        } catch (Exception e) {
            fireEvent(ctx, ImporterEvent.IMPORTER_HANDLER_ERROR, e);
            ExceptionUtils.wrapAndThrow(new ImporterHandlerException(
                    "Importer failure for handler: " + this, e));
        }
        fireEvent(ctx, ImporterEvent.IMPORTER_HANDLER_END);
    }

    public abstract void handle(DocContext ctx) throws IOException;


    private void fireEvent(DocContext ctx, String eventName) {
        fireEvent(ctx, eventName, null);
    }
    private void fireEvent(
            DocContext ctx, String eventName, Exception e) {
        ctx.eventManager().fire(
                ImporterEvent.builder()
                    .name(eventName)
                    .source(this)
                    .document(ctx.doc())
                    .parseState(ctx.parseState())
                    .exception(e)
                    .build());
    }

    //--- Decorators -----------------------------------------------------------

    public static BaseDocumentHandler decorate(
            @NonNull FailableConsumer<DocContext, IOException> consumer) {
        return new FailableConsumerWrapper(consumer);
    }
    public static BaseDocumentHandler decorate(
            @NonNull Consumer<DocContext> consumer) {
        return new ConsumerWrapper(consumer);
    }

    @Data
    static class FailableConsumerWrapper extends BaseDocumentHandler {
        private final FailableConsumer<DocContext, IOException> original;
        @Override
        public void handle(DocContext d) throws IOException {
            original.accept(d);
        }
    }
    @Data
    static class ConsumerWrapper extends BaseDocumentHandler {
        private final Consumer<DocContext> original;
        @Override
        public void handle(DocContext d) throws IOException {
            original.accept(d);
        }
    }
}
