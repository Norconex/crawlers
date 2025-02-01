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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.norconex.importer.handler.condition.ConditionalDocHandler.If;
import com.norconex.importer.handler.condition.ConditionalDocHandler.IfNot;

public class DocHandlerDeserializer extends JsonDeserializer<DocHandler> {

    @Override
    public DocHandler deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        // Delegate to type-aware deserialization
        return deserializeWithType(p, ctxt, null);
    }

    @Override
    public DocHandler deserializeWithType(JsonParser p,
            DeserializationContext ctxt, TypeDeserializer typeDeserializer)
            throws IOException {

        String name = null;
        var currentToken = p.currentToken();
        if (p.currentToken() == JsonToken.END_OBJECT) {
            // Gracefully handle end of object, possibly log a warning
            return null;
        }

        // Check if we are dealing with a wrapped "handler" object
        if (currentToken == JsonToken.START_OBJECT) {
            p.nextToken(); // Move to the first field or END_OBJECT
            name = p.currentName();
            if (p.currentToken() == JsonToken.FIELD_NAME
                    && "handler".equals(name)) {
                p.nextToken(); // Move to the value of "handler"
            }
        }
        if ("if".equals(name)) {
            return ctxt.readValue(p, If.class);
        }
        if ("ifNot".equals(name)) {
            return ctxt.readValue(p, IfNot.class);
        }
        //        if ("reject".equals(name)) {
        //            return ctxt.readValue(p, Reject.class);
        //        }
        // Direct serialization (unwrapped)
        return ctxt.readValue(p, DocHandler.class);
    }
}
