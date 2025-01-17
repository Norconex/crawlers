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
package com.norconex.crawler.core.grid.impl.ignite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.ignite.IgniteException;
import org.apache.ignite.lang.IgniteFuture;
import org.junit.jupiter.api.Test;

class IgniteGridUtilTest {

    @Test
    void testCacheExternalName() {
        var internalName =
                "internalName" + IgniteGridStorage.SUFFIX_SEPARATOR + "suffix";
        var result = IgniteGridUtil.cacheExternalName(internalName);
        assertThat(result).isEqualTo("internalName");
    }

    @Test
    void testBlock() {
        var igniteFuture = mock(IgniteFuture.class);
        when(igniteFuture.get()).thenReturn("future");

        assertThat(IgniteGridUtil.block(igniteFuture)).isEqualTo("future");

        when(igniteFuture.get()).thenThrow(IgniteException.class);
        assertThat(IgniteGridUtil.block(igniteFuture)).isNull();
    }
}
