/* Copyright 2024-2026 Norconex Inc.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Timeout;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.session.CrawlSession;

@ExtendWith(MockitoExtension.class)
@Timeout(30)
class BeforeFsCommandTest {

    @Test
    void testAccept() {
        var session =
                Mockito.mock(CrawlSession.class, Answers.RETURNS_DEEP_STUBS);
        Mockito.when(session.getCrawlContext().getCrawlConfig())
                .thenReturn(new CrawlConfig());
        assertThatNoException()
                .isThrownBy(() -> new BeforeFsCommand().accept(session));
    }
}
