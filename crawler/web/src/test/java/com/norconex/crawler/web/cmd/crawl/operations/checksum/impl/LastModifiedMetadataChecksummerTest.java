/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.cmd.crawl.operations.checksum.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;

class LastModifiedMetadataChecksummerTest {
    @Test
    void testCreateMetadataChecksum() {
        var props = new Properties();
        var checksummer = new LastModifiedMetadataChecksummer();
        assertThat(checksummer.createMetadataChecksum(props)).isNull();

        props.add(LastModifiedMetadataChecksummer.LAST_MODIFIED_FIELD, "blah");
        assertThat(checksummer.createMetadataChecksum(props)).isEqualTo("blah");
    }

    @Test
    void testWriteRead() {
        assertThatNoException()
                .isThrownBy(
                        () -> BeanMapper.DEFAULT.assertWriteRead(
                                new LastModifiedMetadataChecksummer()));
    }
}
