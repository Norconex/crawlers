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
package com.norconex.grid.core.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.SerializationException;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import lombok.NonNull;

//MAYBE: consider moving somewhere more generic if we see value.
public final class SerialUtil {

    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.enable(
                DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        mapper.configure(Feature.AUTO_CLOSE_SOURCE, false);
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        mapper.registerModule(new ParameterNamesModule());
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new JavaTimeModule());
    }

    private SerialUtil() {
    }

    public static JsonFactory jsonFactory() {
        return new JsonFactory(mapper);
    }

    public static JsonGenerator jsonGenerator(@NonNull OutputStream os) {
        try {
            return jsonFactory().createGenerator(os, JsonEncoding.UTF8);
        } catch (IOException e) {
            throw new SerializationException(
                    "Could not create JsonGenerator.", e);
        }
    }

    public static JsonParser jsonParser(@NonNull InputStream is) {
        try {
            return jsonFactory().createParser(
                    new InputStreamReader(is, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SerializationException(
                    "Could not create JsonParser.", e);
        }
    }

    public static String toJsonString(@NonNull Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new SerializationException(
                    "Could not serialize object to JSON: " + object, e);
        }
    }

    public static Reader toJsonReader(@NonNull Object object) {
        return new StringReader(toJsonString(object));
    }

    public static <T> T fromJson(@NonNull String json, @NonNull Class<T> cls) {
        try {
            return mapper.readValue(json, cls);
        } catch (JsonProcessingException e) {
            throw new SerializationException(
                    "Could not deserialize JSON to object: " + json, e);
        }
    }

    public static <T> T fromJson(@NonNull Reader json, @NonNull Class<T> cls) {
        try {
            return mapper.readValue(json, cls);
        } catch (IOException e) {
            throw new SerializationException(
                    "Could not deserialize JSON reader to object." + json, e);
        }
    }

    public static <T> T fromJson(
            @NonNull JsonParser json, @NonNull Class<T> cls) {
        try {
            return mapper.readValue(json, cls);
        } catch (IOException e) {
            throw new SerializationException(
                    "Could not deserialize JSON reader to object." + json, e);
        }
    }
}
