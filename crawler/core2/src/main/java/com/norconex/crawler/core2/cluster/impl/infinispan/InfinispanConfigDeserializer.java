package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

class InfinispanConfigDeserializer
        extends StdDeserializer<ConfigurationBuilderHolder> {
    private static final long serialVersionUID = 1L;

    public InfinispanConfigDeserializer() {
        super(ConfigurationBuilderHolder.class);
    }

    @Override
    public ConfigurationBuilderHolder deserialize(
            JsonParser p, DeserializationContext ctxt) throws IOException {
        var xml = p.readValueAs(String.class);
        try (InputStream is = new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8))) {
            var registry = new ParserRegistry(
                    Thread.currentThread().getContextClassLoader());
            return registry.parse(is, MediaType.APPLICATION_XML);
        } catch (Exception e) {
            throw new IOException("Failed to parse Infinispan config", e);
        }
    }
}
