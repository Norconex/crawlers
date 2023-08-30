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
package com.norconex.crawler.server.TEMP;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.norconex.crawler.server.TEMP.stub.TransportationsConfig;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import lombok.RequiredArgsConstructor;

class MiscTests {

    @RequiredArgsConstructor
    enum Format {
        XML("""
            <config id="my config">
              <transportations>
                <transportation class="Automobile">
                  <configuration>
                     <make>Toyota</make>
                     <model>Camry</model>
                     <year>1800</year>
                  </configuration>
                </transportation>
                <transportation class="Plane">
                  <configuration>
                     <type>COMMERCIAL</type>
                     <name>Boeing 737</name>
                  </configuration>
                </transportation>
              </transportations>
            </config>
            """,
            XmlMapper::new
        ),
        JSON("""
            {
              "id" : "my config",
              "transportations" : [ {
                "class" : "Automobile",
                "configuration" : {
                  "model" : "Camry",
                  "year" : 1800,
                  "make" : "Toyota"
                }
              }, {
                "class" : "Plane",
                "configuration" : {
                  "type" : "COMMERCIAL",
                  "name" : "Boeing 737"
                }
              } ]
            }""",
            ObjectMapper::new
        ),
        YAML("""
            ---
            id: "my config"
            transportations:
            - class: "Automobile"
              configuration:
                model: "Camry"
                year: 1800
                make: "Toyota"
            - class: "Plane"
              configuration:
                type: "COMMERCIAL"
                name: "Boeing 737"
            """,
            () -> new YAMLMapper()
                .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
        )
        ;
        final String source;
        final Supplier<ObjectMapper> mapperSupplier;
    }

    @ParameterizedTest
    @EnumSource(value = Format.class)
    void testValidation(Format format)
            throws JsonMappingException, JsonProcessingException {
        var mapper = format.mapperSupplier.get();
        var cfg = mapper.readValue(format.source, TransportationsConfig.class);
        var factory = Validation.buildDefaultValidatorFactory();
        var validator = factory.getValidator();
        Set<ConstraintViolation<TransportationsConfig>> violations =
                validator.validate(cfg);
        assertThat(violations.size()).isOne();
        assertThat(violations.iterator().next().getMessage()).isEqualTo(
                "must be greater than or equal to 1900");
    }

    @Test
    void testToJonConversion() throws IOException {
        var xmlMapper = new XmlMapper();
        var cfg = xmlMapper.readValue(
                Format.XML.source, TransportationsConfig.class);
        var jsonMapper = new ObjectMapper();
        var out = new StringWriter();
        jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        jsonMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        jsonMapper.writeValue(out, cfg);
        assertThat(out.toString().replaceAll("[\\n\\r]+", "\n"))
            .hasToString(Format.JSON.source);
    }
    @Test
    void testToYamlConversion() throws IOException {
        var xmlMapper = new XmlMapper();
        var cfg = xmlMapper.readValue(
                Format.XML.source, TransportationsConfig.class);
        var yamlMapper = new YAMLMapper();
        var out = new StringWriter();
        yamlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        yamlMapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        yamlMapper.disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID);
        yamlMapper.writeValue(out, cfg);
        assertThat(out.toString().replaceAll("[\\n\\r]+", "\n"))
            .hasToString(Format.YAML.source);
    }
}
