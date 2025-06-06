/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.fs.doc.operations.checksum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.fs.FsTestUtil;
import com.norconex.crawler.fs.doc.FsDocMetadata;

class FsMetadataChecksummerTest {

    @Test
    void testCreateMetadataChecksum() {
        var props = new Properties();
        var checksummer = new FsMetadataChecksummer();
        assertThat(checksummer.createMetadataChecksum(props)).isNull();

        // Just size
        props.add(FsDocMetadata.FILE_SIZE, 111);
        assertThat(checksummer.createMetadataChecksum(props)).isEqualTo("_111");

        // Just date
        props.clear();
        props.add(FsDocMetadata.LAST_MODIFIED, 222);
        assertThat(checksummer.createMetadataChecksum(props)).isEqualTo("222_");

        // both size and date
        props.clear();
        props.add(FsDocMetadata.FILE_SIZE, 333);
        props.add(FsDocMetadata.LAST_MODIFIED, 444);
        assertThat(checksummer.createMetadataChecksum(props)).isEqualTo(
                "444_333");
    }

    @Test
    void testWriteRead() {
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(
                        FsTestUtil.randomize(FsMetadataChecksummer.class)));
    }
}
