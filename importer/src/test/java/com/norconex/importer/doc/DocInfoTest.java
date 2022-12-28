/* Copyright 2022 Norconex Inc.
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
package com.norconex.importer.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.file.ContentType;

class DocInfoTest {

    @Test
    void testDocInfo() {
        var di1 = new DocInfo();
        di1.setReference("ref");
        di1.setContentType(ContentType.BMP);
        di1.setEmbeddedParentReferences(List.of("parentRef"));
        di1.addEmbeddedParentReference("parentRef2");
        di1.setContentEncoding("contentEncoding");

        var di2 = new DocInfo("I will be replaced.");
        di2.copyFrom(di1);
        assertThat(di1).isEqualTo(di2);

        var di3 = new DocInfo();
        di1.copyTo(di3);
        assertThat(di1).isEqualTo(di3);

        var di4 = new DocInfo(di1);
        assertThat(di1).isEqualTo(di4);

        di4 = di4.withReference("ref2", di1);
        assertThat(di1).isNotEqualTo(di4);

        assertThat(di4.getEmbeddedParentReferences())
            .containsExactly("parentRef", "parentRef2");
    }
}
