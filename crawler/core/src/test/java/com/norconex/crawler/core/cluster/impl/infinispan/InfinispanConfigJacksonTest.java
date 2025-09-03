package com.norconex.crawler.core.cluster.impl.infinispan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.Map;
import java.util.stream.Collectors;

import org.infinispan.commons.configuration.io.ConfigurationWriter;
import org.infinispan.commons.configuration.io.xml.XmlConfigurationWriter;
import org.infinispan.configuration.parsing.ConfigurationBuilderHolder;
import org.infinispan.configuration.parsing.ParserRegistry;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

//TODO test yaml + json
class InfinispanConfigJacksonTest {

    private static final String SAMPLE_XML =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <infinispan>
              <cache-container name="local">
                <local-cache name="default"/>
              </cache-container>
            </infinispan>""";

    @Test
    void testDeserializeAndSerialize() throws Exception {
        var mapper = new ObjectMapper();

        // Register custom serializer & deserializer
        var module = new SimpleModule();
        module.addDeserializer(ConfigurationBuilderHolder.class,
                new InfinispanConfigDeserializer());
        module.addSerializer(ConfigurationBuilderHolder.class,
                new InfinispanConfigSerializer());
        mapper.registerModule(module);

        // Deserialize XML string wrapped in JSON string
        // (simulate embedding XML in JSON)
        var jsonWithXml = "\""
                + SAMPLE_XML.replace("\"", "\\\"").replace("\n", "\\n") + "\"";

        var holder =
                mapper.readValue(jsonWithXml, ConfigurationBuilderHolder.class);
        assertNotNull(holder);

        // Serialize back to XML string (wrapped in JSON string)
        var outputJson = mapper.writeValueAsString(holder);
        assertNotNull(outputJson);

        // The XML content should be somewhere inside the JSON string
        assertTrue(outputJson.contains("<cache-container"));
        assertTrue(outputJson.contains("local-cache"));

        // Optional: deserialize again to ensure round-trip works
        var holder2 =
                mapper.readValue(outputJson, ConfigurationBuilderHolder.class);
        assertNotNull(holder2);
    }

    @Test
    void testDeserializeSerializeRoundTripIsEqual() throws Exception {
        var mapper = new ObjectMapper();

        var module = new SimpleModule();
        module.addDeserializer(ConfigurationBuilderHolder.class,
                new InfinispanConfigDeserializer());
        module.addSerializer(ConfigurationBuilderHolder.class,
                new InfinispanConfigSerializer());
        mapper.registerModule(module);

        var jsonWithXml = "\""
                + SAMPLE_XML.replace("\"", "\\\"").replace("\n", "\\n") + "\"";

        var holder1 =
                mapper.readValue(jsonWithXml, ConfigurationBuilderHolder.class);
        var holder2 =
                mapper.readValue(mapper.writeValueAsString(holder1),
                        ConfigurationBuilderHolder.class);

        var parserRegistry = new ParserRegistry(
                Thread.currentThread().getContextClassLoader());

        // Serialize holder1 to XML string
        var writer1 = new StringWriter();
        ConfigurationWriter cw1 =
                new XmlConfigurationWriter(writer1, false, false);
        Map<String, org.infinispan.configuration.cache.Configuration> caches1 =
                holder1.getNamedConfigurationBuilders().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> e.getValue().build()));
        parserRegistry.serialize(cw1,
                holder1.getGlobalConfigurationBuilder().build(), caches1);
        var xml1 = writer1.toString();

        // Serialize holder2 to XML string
        var writer2 = new StringWriter();
        ConfigurationWriter cw2 =
                new XmlConfigurationWriter(writer2, false, false);
        Map<String, org.infinispan.configuration.cache.Configuration> caches2 =
                holder2.getNamedConfigurationBuilders().entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> e.getValue().build()));
        parserRegistry.serialize(cw2,
                holder2.getGlobalConfigurationBuilder().build(), caches2);
        var xml2 = writer2.toString();

        // Compare serialized XML strings
        assertThat(xml1).isEqualTo(xml2);
    }
}
