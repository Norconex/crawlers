/* Copyright 2021-2022 Norconex Inc.
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
import static com.norconex.importer.ImporterEvent.IMPORTER_HANDLER_END;
import static com.norconex.importer.ImporterEvent.IMPORTER_HANDLER_ERROR;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;

import com.norconex.commons.lang.function.FunctionUtil;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.io.IOUtil;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.commons.lang.xml.flow.XMLFlow;
import com.norconex.commons.lang.xml.flow.XMLFlowConsumerAdapter;
import com.norconex.importer.ImporterEvent;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.filter.DocumentFilter;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.handler.filter.OnMatchFilter;
import com.norconex.importer.handler.filter.impl.RejectFilter;
import com.norconex.importer.handler.splitter.DocumentSplitter;
import com.norconex.importer.handler.tagger.DocumentTagger;
import com.norconex.importer.handler.transformer.DocumentTransformer;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumer wrapping an {@link ImporterHandler} instance for use in an
 * {@link XMLFlow}.
 */
@EqualsAndHashCode
@ToString
@Slf4j
public class HandlerConsumer
        implements XMLFlowConsumerAdapter<HandlerContext> {

    private ImporterHandler handler;

    public HandlerConsumer() {
    }
    public HandlerConsumer(ImporterHandler handler) {
        this.handler = handler;
    }

    public ImporterHandler getHandler() {
        return handler;
    }
    public void setHandler(ImporterHandler handler) {
        this.handler = handler;
    }

    public static Consumer<HandlerContext> fromHandlers(
            ImporterHandler... importerHandlers) {
        return fromHandlers(importerHandlers == null
                ? Collections.emptyList() : Arrays.asList(importerHandlers));
    }
    public static Consumer<HandlerContext> fromHandlers(
            List<ImporterHandler> importerHandlers) {
        return FunctionUtil.allConsumers(Optional.ofNullable(importerHandlers)
            .orElseGet(Collections::emptyList)
            .stream()
            .map(HandlerConsumer::new)
            .collect(Collectors.toList()));
    }

    @Override
    public void accept(HandlerContext ctx) {
        if (handler == null || ctx.isRejected()) {
            return;
        }

        fireEvent(ctx, IMPORTER_HANDLER_BEGIN);
        try {
            if (handler instanceof DocumentTagger t) {
                tagDocument(ctx, t);
            } else if (handler instanceof DocumentTransformer t) {
                transformDocument(ctx, t);
            } else if (handler instanceof DocumentSplitter s) {
                splitDocument(ctx, s);
            } else if (handler instanceof DocumentFilter f) {
                acceptDocument(ctx, f);
            } else {
                //TODO instead check if implementing right consumer
                // and invoke if so?
                LOG.error("Unsupported Import Handler: {}", handler);
            }
        } catch (ImporterHandlerException e) {
            fireEvent(ctx, IMPORTER_HANDLER_ERROR, e);
            ExceptionUtils.wrapAndThrow(e);
        } catch (Exception e) {
            fireEvent(ctx, IMPORTER_HANDLER_ERROR, e);
            ExceptionUtils.wrapAndThrow(new ImporterHandlerException(
                    "Importer failure for handler: " + handler, e));
        }
        fireEvent(ctx, IMPORTER_HANDLER_END);
    }

    private void tagDocument(HandlerContext ctx, DocumentTagger tagger)
            throws ImporterHandlerException {
        tagger.tagDocument(
                new HandlerDoc(ctx.getDoc()),
                ctx.getDoc().getInputStream(),
                ctx.getParseState());
    }

    private void acceptDocument(
            HandlerContext ctx, DocumentFilter filter)
                    throws ImporterHandlerException {
        var accepted = filter.acceptDocument(
                new HandlerDoc(ctx.getDoc()),
                ctx.getDoc().getInputStream(),
                ctx.getParseState());
        if (isMatchIncludeFilter(filter)) {
            ctx.getIncludeResolver().setHasIncludes(true);
            if (accepted) {
                ctx.getIncludeResolver().setAtLeastOneIncludeMatch(true);
            }
            return;
        }
        // Deal with exclude and non-OnMatch filters
        if (!accepted){
            ctx.setRejectedBy(filter);
            LOG.debug("Document import rejected. Filter: {}", filter);
        }
    }

    private void transformDocument(
            HandlerContext ctx, DocumentTransformer transformer)
                    throws ImporterHandlerException, IOException {
        var in = ctx.getDoc().getInputStream();
        try (var out =
                ctx.getDoc().getStreamFactory().newOuputStream()) {
            transformer.transformDocument(
                    new HandlerDoc(ctx.getDoc()), in, out, ctx.getParseState());
            CachedInputStream newInputStream = null;
            if (out.isCacheEmpty()) {
                LOG.debug("Transformer \"{}\" returned no content for: {}.",
                        transformer.getClass(), ctx.getDoc().getReference());
                IOUtil.closeQuietly(out);
                newInputStream = in;
            } else {
                in.dispose();
                newInputStream = out.getInputStream();
                IOUtil.closeQuietly(out);
            }
            ctx.getDoc().setInputStream(newInputStream);
        }
    }

    private void splitDocument(
            HandlerContext ctx, DocumentSplitter splitter)
                    throws ImporterHandlerException, IOException {
        List<Doc> childDocs = null;
        var in = ctx.getDoc().getInputStream();
        try (var out =
                ctx.getDoc().getStreamFactory().newOuputStream()) {
            childDocs = splitter.splitDocument(
                    new HandlerDoc(ctx.getDoc()), in, out, ctx.getParseState());
            // If writing was performed, get new content
            if (!out.isCacheEmpty()) {
                ctx.getDoc().setInputStream(out.getInputStream());
                in.dispose();
            }
        }
        if (childDocs != null) {
            for (var i = 0; i < childDocs.size(); i++) {
                var meta = childDocs.get(i).getMetadata();
                meta.add(DocMetadata.EMBEDDED_INDEX, i);
                meta.add(DocMetadata.EMBEDDED_PARENT_REFERENCES,
                        ctx.getDoc().getReference());
            }
            ctx.getChildDocs().addAll(childDocs);
        }
    }

    private boolean isMatchIncludeFilter(DocumentFilter filter) {
        return filter instanceof OnMatchFilter f
                && OnMatch.INCLUDE == f.getOnMatch();
    }

    private void fireEvent(HandlerContext ctx, String eventName) {
        fireEvent(ctx, eventName, null);
    }
    private void fireEvent(
            HandlerContext ctx, String eventName, Exception e) {
        ctx.getEventManager().fire(
                ImporterEvent.builder()
                    .name(eventName)
                    .source(handler)
                    .document(ctx.getDoc())
                    .parseState(ctx.getParseState())
                    .exception(e)
                    .build());
    }

    @Override
    public void loadFromXML(XML xml) {
        if ("reject".equals(xml.getName())) {
            handler = RejectFilter.INSTANCE;
        } else {
            handler = xml.toObjectImpl(ImporterHandler.class);
        }
        if (handler == null) {
            LOG.warn("Importer handler from the following XML resolved to null "
                    + "and will have no effect: {}", xml);
        }
    }
    @Override
    public void saveToXML(XML xml) {
        if (handler instanceof RejectFilter) {
            xml.rename("reject");
        } else {
            xml.rename("handler");
            xml.setAttribute("class", handler.getClass().getName());
            if (handler instanceof XMLConfigurable conf) {
                conf.saveToXML(xml);
            }
        }
    }
}
