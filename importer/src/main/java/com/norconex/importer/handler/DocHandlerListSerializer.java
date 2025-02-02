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

import org.apache.commons.lang3.reflect.FieldUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.norconex.importer.handler.condition.ConditionalDocHandler.If;
import com.norconex.importer.handler.condition.ConditionalDocHandler.IfNot;

public class DocHandlerListSerializer extends JsonSerializer<List<DocHandler>> {

    @Override
    public void serialize(
            List<DocHandler> handlers,
            JsonGenerator gen,
            SerializerProvider sp) throws IOException {

        gen.writeStartArray();
        for (var handler : handlers) {
            if ((handler instanceof If) || (handler instanceof IfNot)) {
                gen.writeObject(handler);
                //                sp.findTypeSerializer(If.class);

            } else {
                gen.writeStartObject();
                gen.writeFieldName("handler");
                gen.writeObject(handler);
                gen.writeEndObject();
            }
        }
        gen.writeEndArray();
    }

    static void print(ToXmlGenerator gen) {
        try {
            var xmlWriter = gen.getStaxWriter();
            var mWriter = FieldUtils.readField(xmlWriter, "mWriter", true);
            var mOut = FieldUtils.readField(mWriter, "mOut", true);
            System.err.println("XXX XML: " + mOut.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
