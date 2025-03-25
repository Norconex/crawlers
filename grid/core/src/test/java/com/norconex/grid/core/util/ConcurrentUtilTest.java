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
package com.norconex.grid.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class ConcurrentUtilTest {

    @SuppressWarnings("unchecked")
    @Test
    void testGet() throws InterruptedException, ExecutionException {
        var future = mock(Future.class);
        when(future.get()).thenReturn("future");

        assertThat(ConcurrentUtil.<String>get(null)).isNull();
        assertThat(ConcurrentUtil.get(future)).isEqualTo("future");

        when(future.get()).thenThrow(InterruptedException.class);
        assertThatExceptionOfType(CompletionException.class)
                .isThrownBy(() -> ConcurrentUtil.get(future))
                .withMessage("Thread was interrupted.");

    }
}
