package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.stream.Collectors;

import org.infinispan.commons.configuration.io.ConfigurationWriter;
import org.infinispan.commons.configuration.io.xml.XmlConfigurationWriter;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

class InfinispanConfigSerializer
        extends JsonSerializer<ConfigurationBuilderHolder> {
    @Override
    public void serialize(
            ConfigurationBuilderHolder value,
            JsonGenerator gen,
            SerializerProvider serializers) throws IOException {
        var parserRegistry = new ParserRegistry(
                Thread.currentThread().getContextClassLoader());

        var stringWriter = new StringWriter();
        ConfigurationWriter writer =
                new XmlConfigurationWriter(stringWriter, true, false);

        var globalConfig =
                value.getGlobalConfigurationBuilder().build();
        Map<String, Configuration> caches =
                value.getNamedConfigurationBuilders()
                        .entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().build()));

        try {
            parserRegistry.serialize(writer, globalConfig, caches);
            gen.writeString(stringWriter.toString());
        } catch (Exception e) {
            throw new IOException(
                    "Failed to serialize Infinispan config", e);
        }
    }
}
