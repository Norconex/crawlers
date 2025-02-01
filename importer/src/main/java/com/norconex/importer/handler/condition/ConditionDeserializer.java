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
package com.norconex.importer.handler.condition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.norconex.importer.handler.condition.Condition.AllOf;
import com.norconex.importer.handler.condition.Condition.AnyOf;
import com.norconex.importer.handler.condition.Condition.NoneOf;

public class ConditionDeserializer
        extends JsonDeserializer<Condition> {

    @Override
    public Condition deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        // Delegate to type-aware deserialization
        return deserializeWithType(p, ctxt, null);
    }

    @Override
    public Condition deserializeWithType(
            JsonParser p,
            DeserializationContext ctxt,
            TypeDeserializer typeDeserializer) throws IOException {

        var currentToken = p.currentToken();
        if (p.currentToken() == JsonToken.END_OBJECT) {
            // Gracefully handle end of object, possibly log a warning
            return null;
        }

        // Check if it's a `ConditionGroup` (wrapped serialization with type ID and array)
        if (currentToken == JsonToken.START_OBJECT) {
            p.nextToken(); // Move to the first field or END_OBJECT

            if (p.currentToken() == JsonToken.FIELD_NAME) {
                var name = p.currentName(); // The type ID from the serializer

                if (!"class".equals(name)) {
                    p.nextToken(); // Move to the START_ARRAY or other token

                    if (p.currentToken() == JsonToken.START_ARRAY) {
                        // Deserialize the array of conditions
                        List<Condition> conditions = new ArrayList<>();
                        while (p.nextToken() != JsonToken.END_ARRAY) {
                            var condition =
                                    ctxt.readValue(p, Condition.class);
                            if (condition != null) {
                                conditions.add(condition);
                            }
                        }
                        p.nextToken(); // move to object end

                        // Return a new `ConditionGroup`
                        if ("anyOf".equals(name)) {
                            return new AnyOf(conditions);
                        }
                        if ("noneOf".equals(name)) {
                            return new NoneOf(conditions);
                        }
                        if ("allOf".equals(name)) {
                            return new AllOf(conditions);
                        }

                        // should not reach here
                        return null;
                    }
                }
            }
        }

        // Fallback: Direct deserialization (unwrapped)
        return ctxt.readValue(p, Condition.class);
    }
}
