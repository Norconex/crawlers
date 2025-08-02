/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core2.cluster.impl.hazelcast;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

public class HazelcastConfigDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(
            JsonParser p, DeserializationContext ctxt)
                    throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        String configPath = node.textValue();
        if (configPath == null || configPath.isBlank()) {
            return null;
        }
        Path path = Paths.get(configPath);
        if (!path.isAbsolute()) {
            String basedir = System.getProperty("basedir");
            if (basedir != null) {
                Path base = Paths.get(basedir);
                path = base.resolve(path);
            }
        }
        return path.toString();
    }
}