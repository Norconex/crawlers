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
package com.norconex.crawler.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.cluster.support.InMemoryCacheMap;

class CrawlAttributesTest {

    private InMemoryCacheMap<String> cache;
    private CrawlAttributes attrs;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCacheMap<>("test-attrs");
        attrs = new CrawlAttributes(cache);
    }

    @Test
    void setAndGetString() {
        attrs.setString("key", "value");
        assertThat(attrs.getString("key")).isPresent().hasValue("value");
    }

    @Test
    void getString_missingKey_returnsEmpty() {
        assertThat(attrs.getString("missing")).isEmpty();
    }

    @Test
    void setStringIfAbsent_newKey_returnsTrue() {
        assertThat(attrs.setStringIfAbsent("newKey", "v1")).isTrue();
        assertThat(attrs.getString("newKey")).hasValue("v1");
    }

    @Test
    void setStringIfAbsent_existingKey_returnsFalse() {
        attrs.setString("existingKey", "original");
        assertThat(attrs.setStringIfAbsent("existingKey", "new")).isFalse();
        assertThat(attrs.getString("existingKey")).hasValue("original");
    }

    @Test
    void setAndGetBoolean_true() {
        attrs.setBoolean("flag", true);
        assertThat(attrs.getBoolean("flag")).isTrue();
    }

    @Test
    void setAndGetBoolean_false() {
        attrs.setBoolean("flag", false);
        assertThat(attrs.getBoolean("flag")).isFalse();
    }

    @Test
    void getBoolean_missingKey_returnsFalse() {
        assertThat(attrs.getBoolean("absent")).isFalse();
    }

    @Test
    void setBooleanIfAbsent_newKey_returnsTrue() {
        assertThat(attrs.setBooleanIfAbsent("b", true)).isTrue();
        assertThat(attrs.getBoolean("b")).isTrue();
    }

    @Test
    void setBooleanIfAbsent_existingKey_returnsFalse() {
        attrs.setBoolean("b", false);
        assertThat(attrs.setBooleanIfAbsent("b", true)).isFalse();
        assertThat(attrs.getBoolean("b")).isFalse();
    }

    @Test
    void setAndGetInteger() {
        attrs.setInteger("count", 42);
        assertThat(attrs.getInteger("count")).isEqualTo(42);
    }

    @Test
    void getInteger_missingKey_returnsZero() {
        assertThat(attrs.getInteger("absent")).isEqualTo(0);
    }

    @Test
    void setIntegerIfAbsent_newKey_returnsTrue() {
        assertThat(attrs.setIntegerIfAbsent("i", 7)).isTrue();
        assertThat(attrs.getInteger("i")).isEqualTo(7);
    }

    @Test
    void setIntegerIfAbsent_existingKey_returnsFalse() {
        attrs.setInteger("i", 3);
        assertThat(attrs.setIntegerIfAbsent("i", 99)).isFalse();
        assertThat(attrs.getInteger("i")).isEqualTo(3);
    }

    @Test
    void getCache_returnsSameCache() {
        assertThat(attrs.getCache()).isSameAs(cache);
    }
}
