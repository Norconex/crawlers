/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.fs.callbacks;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.fs.mock.MockFsCrawlerBuilder;

class BeforeFsCommandTest {

    @TempDir
    private Path tempDir;

    @Test
    void testAccept() {
        var ctx = new MockFsCrawlerBuilder(tempDir).crawlerContext();
        ctx.init();
        assertThatNoException()
                .isThrownBy(() -> new BeforeFsCommand().accept(ctx));
    }
}
