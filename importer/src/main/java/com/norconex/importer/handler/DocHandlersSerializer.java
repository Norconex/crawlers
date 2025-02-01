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

import org.apache.commons.lang3.reflect.FieldUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.norconex.importer.handler.condition.ConditionalDocHandler.If;
import com.norconex.importer.handler.condition.ConditionalDocHandler.IfNot;

public class DocHandlersSerializer extends JsonSerializer<List<DocHandler>> {

    @Override
    public void serialize(
            List<DocHandler> handlers,
            JsonGenerator gen,
            SerializerProvider sp) throws IOException {

        var isXml = gen instanceof ToXmlGenerator;
        if (isXml) {

            //            sp.findValueSerializer(JsonXmlCollectionSerializer.class);
            //
            //            var tempWriter = new StringWriter();
            //            var xmlMapper = (XmlMapper) gen.getCodec();
            //            var tempGen =
            //                    xmlMapper.getFactory().createGenerator(tempWriter);

            //            serializer.serialize(handlers, tempGen, sp);

            //            tempGen.writeFieldName("handlers");

            //            var xmlGen = (ToXmlGenerator) gen;

            //            var innerName = innerName();
            //            tempGen.setNextName(QName.valueOf("blah"));
            //
            //            var first = true;

            var xmlGen = (ToXmlGenerator) gen;
            sp.findValueSerializer(If.class);
            sp.findValueSerializer(IfNot.class);

            for (var handler : handlers) {
                print(xmlGen);
                if ((handler instanceof If) || (handler instanceof IfNot)) {
                    //                    xmlGen.setNextName(QName.valueOf("if"));
                    //                  ifSer.serialize(handler, gen, sp);
                } else {
                    xmlGen.setNextName(QName.valueOf("handler"));
                    gen.writeObject(handler);
                }
                print(xmlGen);
            }

            //            tempGen.flush();
            //            var xmlOutput = tempWriter.toString();
            //            System.err.println("XXX OUTPUT: " + xmlOutput);
            //            gen.writeRaw(xmlOutput);

        } else {

            gen.writeStartArray();
            for (Object handler : handlers) {
                gen.writeObject(handler);
            }
            gen.writeEndArray();
        }
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
