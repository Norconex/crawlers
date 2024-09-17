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
package com.norconex.importer.handler;

import static com.norconex.importer.handler.ScriptRunner.VELOCITY_ENGINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

class ScriptRunnerTest {

    @Test
    void testWithScript() {
        var script1 = "#set($content = \"abc\")";
        var script2 = "#set($content = \"def\")";
        var runner1 = new ScriptRunner<>(VELOCITY_ENGINE, script1);
        var runner2 = runner1.withScript(script2);

        // check scripts are different but engine remained the same
        assertThat(runner1.getScript()).isEqualTo(script1);
        assertThat(runner2.getScript()).isEqualTo(script2);
        assertThat(runner1.getEngineName()).isEqualTo(VELOCITY_ENGINE);
        assertThat(runner2.getEngineName()).isEqualTo(VELOCITY_ENGINE);
    }

    @Test
    void testInvalidEngine() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ScriptRunner<>("badone", ""));
    }
}
