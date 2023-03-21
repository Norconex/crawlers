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
package com.norconex.importer.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;

class CommonAttributesResolverTest {

    @Test
    void testResolveDoc() {
        var doc = TestUtil.getAlicePdfDoc();

        CommonAttributesResolver.resolve(doc);

        assertThat(doc.getDocRecord().getContentEncoding()).isEqualTo(
                "UTF-16LE");
        assertThat(doc.getDocRecord().getContentType()).isEqualTo(
                ContentType.PDF);

        assertThat(doc.getMetadata().getString(
                DocMetadata.CONTENT_ENCODING)).isEqualTo("UTF-16LE");
        assertThat(doc.getMetadata().getString(
                DocMetadata.CONTENT_TYPE)).isEqualTo("application/pdf");
        assertThat(doc.getMetadata().getString(
                DocMetadata.CONTENT_FAMILY)).isEqualTo("pdf");
    }
}
