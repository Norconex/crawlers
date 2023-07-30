/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.server.api.common.config;

import java.util.function.Consumer;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerSentEvent;

import com.norconex.commons.lang.event.Event;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlDocDTO;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

@Configuration
public class OpenApiConfigurer {

    private static final String SSE_DESC =  """
            A <a href="https://developer.mozilla.org/en-US/docs/Web/API/\
            Server-sent_events/Using_server-sent_events" target="BLANK">\
            server-sent event</a>, to be consumed as part of a
            stream of server-sent events. Refer to the operation
            documentation for expected "data" field schemas.""";

    @Bean
    public OpenApiCustomizer customizeOpenApi() {
        return openApi -> {
            addSchema(
                openApi,
                ServerSentEvent.class,
                "ServerSentEventObject",
                schema -> schema.setDescription(SSE_DESC));
            addSchema(openApi, CrawlDocDTO.class, "CrawlDoc");
            addSchema(openApi, Event.class, "CrawlEvent");
        };
    }

    private void addSchema(
            OpenAPI openApi, Class<?> cls, String name) {
        addSchema(openApi, cls, name, null);
    }
    private void addSchema(
            OpenAPI openApi, Class<?> cls, String name, Consumer<Schema<?>> c) {
        var schema = ModelConverters.getInstance()
                .readAllAsResolvedSchema(new AnnotatedType(cls)).schema;
        if (c != null) {
            c.accept(schema);
        }
        openApi.getComponents().addSchemas(name, schema);
    }
}