/* Copyright 2025 Norconex Inc.
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
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.text.WordUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.norconex.importer.handler.condition.Condition;
import com.norconex.importer.handler.condition.ConditionGroup;
import com.norconex.importer.handler.condition.ConditionalDocHandler;
import com.norconex.importer.handler.condition.If;
import com.norconex.importer.handler.condition.IfNot;

public class DocHandlerListSerializer extends JsonSerializer<List<DocHandler>> {

    @Override
    public void serialize(
            List<DocHandler> handlers,
            JsonGenerator gen,
            SerializerProvider sp) throws IOException {

        if (gen instanceof ToXmlGenerator xmlGen) {
            writeXmlDocHandlerList(handlers, xmlGen, true);
            return;
        }

        gen.writeStartArray();
        for (var handler : handlers) {
            if ((handler instanceof If) || (handler instanceof IfNot)) {
                gen.writeObject(handler);
            } else {
                gen.writeStartObject();
                gen.writeFieldName(DocHandler.NAME);
                gen.writeObject(handler);
                gen.writeEndObject();
            }
        }
        gen.writeEndArray();
    }

    private void writeXmlDocHandlerList(
            List<DocHandler> handlers,
            ToXmlGenerator gen,
            boolean isRoot)
            throws IOException {
        var first = true;
        for (var handler : handlers) {
            if ((handler instanceof ConditionalDocHandler condHandler)) {
                writeXmlConditionalHandler(
                        condHandler.getName(), condHandler, gen);
            } else {
                // no idea why, but first field name can't be written.
                if (!isRoot || !first) {
                    gen.writeFieldName(DocHandler.NAME);
                } else {
                    gen.setNextName(QName.valueOf(DocHandler.NAME));
                }
                gen.writeObject(handler);
            }
            first = false;
        }
    }

    private void writeXmlConditionalHandler(
            String tagName, ConditionalDocHandler condHandler,
            ToXmlGenerator gen)
            throws IOException {
        gen.writeRaw("<%s>".formatted(tagName));
        gen.flush();

        writeXmlCondition(condHandler.getCondition(), gen);

        gen.writeRaw("<then>");
        gen.flush();
        writeXmlDocHandlerList(condHandler.getThenHandlers(), gen, false);
        gen.writeRaw("</then>");
        gen.flush();

        if (!condHandler.getElseHandlers().isEmpty()) {
            gen.writeRaw("<else>");
            gen.flush();
            writeXmlDocHandlerList(condHandler.getElseHandlers(), gen, false);
            gen.writeRaw("</else>");
            gen.flush();
        }
        //
        gen.writeRaw("</%s>".formatted(tagName));
        gen.flush();
    }

    private void writeXmlCondition(
            Condition condition, ToXmlGenerator gen)
            throws IOException {
        if (condition instanceof ConditionGroup condGroup) {
            var tag = WordUtils
                    .uncapitalize(condGroup.getClass().getSimpleName());
            gen.writeRaw("<condition>");
            gen.writeRaw("<%s>".formatted(tag));
            gen.flush();
            for (var cond : condGroup.getConditions()) {
                gen.writeFieldName("condition");
                writeXmlCondition(cond, gen);
            }
            gen.writeRaw("</%s>".formatted(tag));
            gen.writeRaw("</condition>");
            gen.flush();
        } else {
            gen.setNextName(QName.valueOf("condition"));
            gen.writeObject(condition);
        }
    }
}
