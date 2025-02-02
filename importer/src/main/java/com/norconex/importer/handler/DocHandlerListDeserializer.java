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
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.importer.handler.condition.Condition;
import com.norconex.importer.handler.condition.Condition.AllOf;
import com.norconex.importer.handler.condition.Condition.AnyOf;
import com.norconex.importer.handler.condition.Condition.ConditionGroup;
import com.norconex.importer.handler.condition.Condition.NoneOf;
import com.norconex.importer.handler.condition.ConditionalDocHandler;
import com.norconex.importer.handler.condition.ConditionalDocHandler.If;
import com.norconex.importer.handler.condition.ConditionalDocHandler.IfNot;

public class DocHandlerListDeserializer
        extends JsonDeserializer<List<DocHandler>> {

    @Override
    public List<DocHandler> deserialize(JsonParser p,
            DeserializationContext ctxt)
            throws IOException {
        // Delegate to type-aware deserialization
        return deserializeWithType(p, ctxt, null);
    }

    @Override
    public List<DocHandler> deserializeWithType(
            JsonParser p,
            DeserializationContext ctxt,
            TypeDeserializer typeDeserializer) throws IOException {

        if (p.currentToken() == JsonToken.END_OBJECT) {
            // Should not get here, but just in case, handle end of object
            return List.of();
        }

        if (p.getCodec() instanceof XmlMapper) {
            return readXmlDocHandlerList(p, ctxt);
        }

        if (p.currentToken() == JsonToken.START_ARRAY) {
            List<DocHandler> handlers = new ArrayList<>();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                // Process an arrary entry: either a handler of if/ifNot
                if (p.currentToken() == JsonToken.START_OBJECT) {
                    p.nextToken(); // Move to the first field or END_OBJECT
                    if (p.currentToken() == JsonToken.FIELD_NAME) {
                        var name = p.currentName();
                        if ("if".equals(name)) {
                            var ifHandler = ctxt.readValue(p, If.class);
                            handlers.add(ifHandler);
                        } else if ("ifNot".equals(name)) {
                            var ifnotHandler = ctxt.readValue(p, IfNot.class);
                            handlers.add(ifnotHandler);
                        } else {
                            // dealing with handler, move to object start
                            p.nextToken();
                            var handler = ctxt.readValue(p, DocHandler.class);
                            if (handler != null) {
                                handlers.add(handler);
                            }
                            p.nextToken(); // move to object end
                        }
                    }
                }
            }
            return handlers;
        }
        // should not reach here
        return null;
    }

    private static int indent = 0;

    private void logOpen(String tag) {
        System.err.println(StringUtils.repeat(' ', indent) + "<" + tag + ">");
        indent++;
    }

    private void logClose(String tag) {
        indent--;
        System.err.println(StringUtils.repeat(' ', indent) + "</" + tag + ">");
    }

    private List<DocHandler> readXmlDocHandlerList(
            JsonParser p,
            DeserializationContext ctxt) throws IOException {

        List<DocHandler> handlers = new ArrayList<>();
        while (p.nextToken() != JsonToken.END_OBJECT) {
            var name = p.currentName();
            if ("if".equals(name)) {
                logOpen("if");
                handlers.add(readXmlConditionalHandler(If.class, p, ctxt));
                logClose("if");
            } else if ("ifNot".equals(name)) {
                logOpen("ifNot");
                handlers.add(readXmlConditionalHandler(IfNot.class, p, ctxt));
                logClose("ifNot");
            } else if ("handler".equals(name)) {
                logOpen("handler");
                // dealing with handler, move to object start
                p.nextToken();
                var handler = ctxt.readValue(p, DocHandler.class);
                if (handler != null) {
                    handlers.add(handler);
                }
                logClose("handler");
            }
        }

        //
        //        System.err.println("XXX next token: " + p.nextToken());
        //        System.err.println("XXX next name: " + p.currentName());
        //
        return handlers;

    }

    private ConditionalDocHandler readXmlConditionalHandler(
            Class<? extends ConditionalDocHandler> cls,
            JsonParser p,
            DeserializationContext ctxt) throws IOException {

        ConditionalDocHandler condHandler = ClassUtil.newInstance(cls);

        p.nextToken(); // move to conditional handler object start

        while (p.nextToken() != JsonToken.END_OBJECT) {
            var name = p.currentName();
            if ("condition".equals(name)) {
                condHandler.setCondition(readXmlCondition(p, ctxt));
            } else if ("then".equals(name)) {
                logOpen("then");
                p.nextToken(); // move to start object for list
                condHandler.setThenHandlers(readXmlDocHandlerList(p, ctxt));
                // p.nextToken(); // move to end object for list
                logClose("then");
            } else if ("else".equals(name)) {
                logOpen("else");
                p.nextToken(); // move to start object for list
                condHandler.setElseHandlers(readXmlDocHandlerList(p, ctxt));
                //  p.nextToken(); // move to end object for list
                logClose("else");
            }
        }
        //        System.err.println("XXX cond handler: " + condHandler);
        return condHandler;
    }

    private Condition readXmlCondition(
            JsonParser p,
            DeserializationContext ctxt) throws IOException {

        // next token is either a class or a condition group
        p.nextToken();

        // advance to group object start or "class":
        p.nextToken();

        var name = p.currentName();
        if ("anyOf".equals(name)) {
            logOpen("anyOf");
            var grp = readXmlConditionGroup(AnyOf.class, p, ctxt);
            logClose("anyOf");
            return grp;
        }
        if ("allOf".equals(name)) {
            logOpen("allOf");
            var grp = readXmlConditionGroup(AllOf.class, p, ctxt);
            logClose("allOf");
            return grp;
        }
        if ("noneOf".equals(name)) {
            logOpen("noneOf");
            var grp = readXmlConditionGroup(NoneOf.class, p, ctxt);
            logClose("noneOf");
            return grp;
        }

        //        System.err.println("  XXX COND REGULAR");
        logOpen("condition");
        var cnd = ctxt.readValue(p, Condition.class);
        logClose("condition");
        return cnd;
    }

    private Condition readXmlConditionGroup(
            Class<? extends ConditionGroup> cls,
            JsonParser p,
            DeserializationContext ctxt) throws IOException {

        List<Condition> conditions = new ArrayList<>();

        p.nextToken(); // move to first child condition

        while (p.nextToken() != JsonToken.END_OBJECT) {
            //            var name = p.currentName();
            conditions.add(readXmlCondition(p, ctxt));
        }
        p.nextToken(); // move to object end (of "array")

        //        System.err.println("XXX current token: " + p.currentToken());
        //        System.err.println("XXX current name: " + p.currentName());

        return ClassUtil.newInstance(cls, conditions);
    }
}
