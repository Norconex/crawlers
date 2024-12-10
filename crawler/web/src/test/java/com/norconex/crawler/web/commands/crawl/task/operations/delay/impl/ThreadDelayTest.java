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
package com.norconex.crawler.web.commands.crawl.task.operations.delay.impl;

import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class ThreadDelayTest {

    @Test
    void testDelay() {
        var threadDelay = new ThreadDelay();
        assertThatNoException().isThrownBy(() -> {
            threadDelay.delay(0, "http://mydomain1.com/somepage.html");
            threadDelay.delay(10, "http://mydomain1.com/somepage.html");
            threadDelay.delay(10, "http://mydomain2.com/somepage.html");
        });
    }
}
