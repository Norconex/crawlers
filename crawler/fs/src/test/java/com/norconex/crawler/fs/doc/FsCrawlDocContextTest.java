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
package com.norconex.crawler.fs.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.norconex.importer.doc.DocContext;

class FsCrawlDocContextTest {

    @Test
    void test() {
        // Should make absolute
        var rec = new FsCrawlDocContext("ref");
        assertThat(rec.getReference()).isEqualTo(
                new File("ref").getAbsolutePath());

        // Already absolute on windows, do not change
        rec = new FsCrawlDocContext(new DocContext("c:\\ref"));
        assertThat(rec.getReference()).isEqualTo("c:\\ref");

        // Not a local file, do not change
        rec = new FsCrawlDocContext("cmis:http://blah.com");
        assertThat(rec.getReference()).isEqualTo("cmis:http://blah.com");
    }
}
