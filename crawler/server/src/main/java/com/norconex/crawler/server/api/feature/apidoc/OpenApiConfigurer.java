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
package com.norconex.crawler.server.api.feature.apidoc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norconex.commons.lang.ClassFinder;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.crawler.server.api.common.CrawlerServerException;
import com.norconex.crawler.server.api.common.ServerSentEventName;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlDocDTO;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlEventDTO;
import com.norconex.crawler.web.canon.CanonicalLinkDetector;
import com.norconex.crawler.web.robot.RobotsTxtProvider;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

@Configuration
public class OpenApiConfigurer {
    //TODO if Configurable consider all props as being "ignored"
    // (leaving it to Configuration class).
    //TODO check if in the ModelConverter or JSON object mapper config
    // , we can intercept when
    // there is no mapping for an ID and if a fully qualified
    // class is found that matches that, use that.
    //TODO also use service loader to add packages to scan...
    // in case third-parties don't want to use fully qualified names.
    //TODO maybe have ways to add packages to scan as well.
    //TODO use GroupOpenApi bean
    //TODO Add main ones directly, then scan for all Configurable classes
    //TODO maybe derive schema name from class name?

    private static final Set<Class<?>> POLYMORPHABLES = new HashSet<>(Set.of(
            CanonicalLinkDetector.class,
//            EventListener<Event>
            ReferenceFilter.class,
            RobotsTxtProvider.class
    ));

    private static final String SSE_DESC =  """
            A <a href="https://developer.mozilla.org/en-US/docs/Web/API/\
            Server-sent_events/Using_server-sent_events" target="BLANK">\
            server-sent event</a>, to be consumed as part of a
            stream of server-sent events. Refer to the operation
            documentation for expected "data" field schemas.""";

    private final Set<Class<?>> subTypes = new HashSet<>();

    @Bean
    public OpenApiCustomizer customizeOpenApi() {
        return openApi -> {
            addServerSentEventSchemas(openApi);
            addCrawlSampleSchemas(openApi);
            addPolymorphicSchemas(openApi);
            addConfigurableConfigSchemas(openApi);
        };
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        //TODO we should likely share this logic with the V4 config
        // loader in general.
        var mapper = builder.build();
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.registerSubtypes(getSubTypes());
        return mapper;
    }

    public Set<Class<?>> getSubTypes() {
        return Collections.unmodifiableSet(subTypes);
    }

    private void addServerSentEventSchemas(OpenAPI openApi) {
        addSchema(
                openApi,
                ServerSentEvent.class,
                "ServerSentEventObject",
                schema -> schema.setDescription(SSE_DESC));
        addSchema(openApi,
                ServerSentEventName.class, "ServerSentEventName");

    }

    private void addCrawlSampleSchemas(OpenAPI openApi) {
        addSchema(openApi, CrawlDocDTO.class, "CrawlDoc");
        addSchema(openApi, CrawlEventDTO.class, "CrawlEvent");
    }

    // polymorphic schemas
    private void addPolymorphicSchemas(OpenAPI openApi) {
        POLYMORPHABLES.forEach(type -> {
            List<String> subSchemaNames = new ArrayList<>();
            findSubTypes(type).forEach(subType -> {
                var schemaName = Optional.ofNullable(subType.getAnnotation(
                        io.swagger.v3.oas.annotations.media.Schema.class))
                      .map(io.swagger.v3.oas.annotations.media.Schema::name)
                      .orElseGet(subType::getSimpleName);
                subSchemaNames.add(schemaName);
                subTypes.add(subType);
                addSchema(openApi, subType, schemaName);
            });

            var classEnum = new StringSchema();
            addSchema(openApi, type, schema -> {
                subSchemaNames.forEach(name -> {
                    schema.addOneOfItem(new ObjectSchema().$ref(
                            AnnotationsUtils.COMPONENTS_REF + name));
                    classEnum.addEnumItem(name);
                });
                schema.addProperty("class", classEnum);

            });
        });
    }

    private void addConfigurableConfigSchemas(OpenAPI openApi) {
        // if a type is configurable, make sure to register its config
        // if not already.
        findSubTypes(Configurable.class).forEach(cls -> {
            //TODO ok to support only those already registered?
            //TODO shall we support multi-nesting of configurables?
            if (hasRegisteredSchema(openApi, cls)) {
                try {
                    Class<?> subCls =
                            cls.getMethod("getConfiguration").getReturnType();
                    if (!hasRegisteredSchema(openApi, subCls)) {
                        addSchema(openApi, subCls, subCls.getSimpleName());
                    }
                } catch (NoSuchMethodException | SecurityException e) {
                    throw new CrawlerServerException(e);
                }
            }
        });
    }

    private <T> List<Class<? extends T>> findSubTypes(Class<T> type) {
        //TODO if returning empty or null does not cut it, return Void when none
        return ClassFinder.findSubTypes(type, c ->
            c.startsWith("com.norconex.")
            && !c.startsWith("com.norconex.crawler.server")
        );
    }
    private Schema<?> addSchema(
            OpenAPI openApi, Class<?> cls, String name, Consumer<Schema<?>> c) {
        var schema = ModelConverters.getInstance()
                .readAllAsResolvedSchema(new AnnotatedType(cls)).schema;
        var schemaName = name != null ? name : schema.getName();
        if (c != null) {
            c.accept(schema);
        }
        openApi.getComponents().addSchemas(schemaName, schema);
        return schema;
    }
    private Schema<?> addSchema(OpenAPI openApi, Class<?> cls, String name) {
        return addSchema(openApi, cls, name, null);
    }
    private Schema<?> addSchema(
            OpenAPI openApi, Class<?> cls, Consumer<Schema<?>> c) {
        return addSchema(openApi, cls, null, c);
    }
    private boolean hasRegisteredSchema(OpenAPI openApi, Class<?> cls) {
        return openApi.getComponents().getSchemas().containsKey(
                cls.getSimpleName());
    }
}