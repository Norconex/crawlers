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
package com.norconex.grid.core.util;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.SystemUtil;
import com.norconex.grid.core.junit.WithLogLevel;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@WithLogLevel(value = "DEBUG", classes = ScopedThreadFactoryCreator.class)
class ScopedThreadFactoryCreatorTest {
    @Test
    void testScopedThreadFactoryCreator() {
        assertThatNoException().isThrownBy(() -> {
            var stfc = new ScopedThreadFactoryCreator("someScope");
            var exec =
                    Executors.newFixedThreadPool(1, stfc.create("someThread"));
            ConcurrentUtil.get(CompletableFuture.runAsync(
                    () -> SystemUtil.runAndCaptureOutput(() -> {
                        LOG.debug("I'm in some thread.");
                    }), exec));
            exec.shutdown();
        });
    }
}
