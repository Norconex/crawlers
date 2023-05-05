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
package com.norconex.importer.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;

class EmbeddedConfigTest {

    @Test
    void testEmbeddedConfig() {

        var cfg = new EmbeddedConfig();

        assertThatNoException().isThrownBy(() -> XML.assertWriteRead(
                cfg, "embedded"));

        assertThat(cfg.isEmpty()).isTrue();

        cfg.setSplitEmbeddedOf(List.of(
                TextMatcher.basic("splitBlah"),
                TextMatcher.regex(".*splitBlah.*")));
        cfg.setSkipEmmbbededOf(List.of(
                TextMatcher.basic("skipOfBlah"),
                TextMatcher.regex(".*skipOfBlah.*")));
        cfg.setSkipEmmbbeded(List.of(
                TextMatcher.basic("skipBlah"),
                TextMatcher.regex(".*skipBlah.*")));

        assertThat(cfg.isEmpty()).isFalse();

        assertThatNoException().isThrownBy(() -> XML.assertWriteRead(
                cfg, "embedded"));
    }
}
