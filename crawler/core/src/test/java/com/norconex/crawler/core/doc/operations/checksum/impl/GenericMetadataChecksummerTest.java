/* Copyright 2017-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations.checksum.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;

class GenericMetadataChecksummerTest {

    @Test
    void testWriteRead() {
        var c = new GenericMetadataChecksummer();
        c.getConfiguration()
            .setFieldMatcher(TextMatcher.basic("blah"))
            .setKeep(true)
            .setToField("myToField")
            .setOnSet(PropertySetter.OPTIONAL);
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(c));
    }

    @Test
    void testGenericMetadataChecksummer() {
        var props = new Properties();
        props.set("field1", "value1");
        props.set("field2", "value2");
        props.set("field3", "value3");

        var c = new GenericMetadataChecksummer();
        c.getConfiguration()
            .setFieldMatcher(TextMatcher.regex("field.*"))
            .setKeep(true)
            .setToField("myfield");

        c.createMetadataChecksum(props);

        assertThat(props.getString("myfield")).isEqualTo(
                "field1=value1;field2=value2;field3=value3;");
    }
}
