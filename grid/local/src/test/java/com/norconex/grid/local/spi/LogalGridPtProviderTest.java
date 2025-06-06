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
package com.norconex.grid.local.spi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.grid.core.GridConnector;
import com.norconex.grid.local.LocalGridConnector;

class LogalGridPtProviderTest {

    @Test
    void testGetPolymorphicTypes() {
        var provider = new LocalGridPtProvider();
        assertThat(provider.getPolymorphicTypes().keySet())
                .contains(GridConnector.class);
        assertThat(provider.getPolymorphicTypes().values())
                .contains(LocalGridConnector.class);
    }
}
