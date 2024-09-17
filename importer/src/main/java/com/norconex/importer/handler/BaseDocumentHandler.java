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
package com.norconex.importer.handler;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.norconex.importer.ImporterEvent;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Base class or wrapper class that makes pre/post parse consumers integrate
 * smoother with the importer process. It will fire events before and after
 * an handler is invoked, and handle returned exceptions.
 * While not mandatory, it is recommended to extend this class or wrap your
 * existing consumer instance with it.
 */
@ToString
@EqualsAndHashCode
public abstract class BaseDocumentHandler implements DocumentHandler {

    //Maybe provide a base doc handler config which allows
    // filtering on content type or reference. Humm... that looks like restrictTo

    @Override
    public final void accept(HandlerContext ctx) {
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
            // be safe, and flush any written content
            ctx.flush();
        } catch (IOException e) {
            fireEvent(ctx, ImporterEvent.IMPORTER_HANDLER_ERROR, e);
            throw new UncheckedIOException(
                    "Importer failure for handler: " + this, e);
        }
        fireEvent(ctx, ImporterEvent.IMPORTER_HANDLER_END);
    }

    protected abstract void handle(HandlerContext ctx) throws IOException;

    private void fireEvent(HandlerContext ctx, String eventName) {
        fireEvent(ctx, eventName, null);
    }

    private void fireEvent(
            HandlerContext ctx, String eventName, Exception e) {
        ctx.eventManager().fire(
                ImporterEvent.builder()
                        .name(eventName)
                        .source(this)
                        .document(ctx.doc())
                        .parseState(ctx.parseState())
                        .exception(e)
                        .build());
    }
}
