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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.norconex.importer.handler.condition.Condition.ConditionGroup;

public class ConditionSerializer extends JsonSerializer<Condition> {

    @Override
    public void serialize(Condition value, JsonGenerator gen,
            SerializerProvider serializers) throws IOException {
        //NOOP
    }

    @Override
    public void serializeWithType(Condition value, JsonGenerator gen,
            SerializerProvider serializers, TypeSerializer typeSer)
            throws IOException {

        if (value instanceof ConditionGroup group
                && !group.conditions.isEmpty()) {
            gen.writeStartObject();
            gen.writeFieldName(typeSer.getTypeIdResolver().idFromValue(value));

            gen.writeStartArray();
            for (Condition condition : group.conditions) {
                if (condition != null) {
                    serializers.defaultSerializeValue(condition, gen);
                }
            }
            gen.writeEndArray();
            gen.writeEndObject();
        } else {
            gen.writeObject(value);
        }
    }
}
