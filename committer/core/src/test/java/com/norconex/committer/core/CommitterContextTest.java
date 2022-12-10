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
package com.norconex.committer.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.io.CachedStreamFactory;

class CommitterContextTest {

    @Test
    void testCommitterContext() {
        var eventManager = new EventManager();
        var csf = new CachedStreamFactory();
        var workDir = Paths.get("workdir");

        var ctx = CommitterContext.builder().build();

        // assert that defaults are set, but they are not the same
        assertThat(ctx.getEventManager()).isNotNull().isNotSameAs(eventManager);
        assertThat(ctx.getStreamFactory()).isNotNull().isNotSameAs(csf);
        assertThat(ctx.getWorkDir()).isNotNull().isNotSameAs(workDir);

        ctx = ctx.withEventManager(eventManager);
        ctx = ctx.withStreamFactory(csf);
        ctx = ctx.withWorkdir(workDir);
        // assert that they are now the same
        assertThat(ctx.getEventManager()).isSameAs(eventManager);
        assertThat(ctx.getStreamFactory()).isSameAs(csf);
        assertThat(ctx.getWorkDir()).isSameAs(workDir);
    }
}
