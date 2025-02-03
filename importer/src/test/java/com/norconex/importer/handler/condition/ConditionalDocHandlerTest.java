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
package com.norconex.importer.handler.condition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.event.EventManager;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.DocHandlerContext;

class ConditionalDocHandlerTest {

    @Test
    void testIf() {
        assertThatNoException().isThrownBy(() -> {
            var ctx = DocHandlerContext
                    .builder()
                    .doc(TestUtil.getAliceHtmlDoc())
                    .eventManager(new EventManager())
                    .build();
            var iff = new If();

            iff.setThenHandlers(List.of(dhc -> true));
            iff.setElseHandlers(List.of(dhc -> false));

            iff.setCondition(dhc -> true);
            assertThat(iff.handle(ctx)).isTrue();
            iff.setCondition(dhc -> false);
            assertThat(iff.handle(ctx)).isFalse();
        });
    }

    @Test
    void testIfNot() {
        assertThatNoException().isThrownBy(() -> {
            var ctx = DocHandlerContext
                    .builder()
                    .doc(TestUtil.getAliceHtmlDoc())
                    .eventManager(new EventManager())
                    .build();
            var ifNot = new IfNot();

            ifNot.setThenHandlers(List.of(dhc -> true));
            ifNot.setElseHandlers(List.of(dhc -> false));

            ifNot.setCondition(dhc -> true);
            assertThat(ifNot.handle(ctx)).isFalse();
            ifNot.setCondition(dhc -> false);
            assertThat(ifNot.handle(ctx)).isTrue();
        });
    }

}
