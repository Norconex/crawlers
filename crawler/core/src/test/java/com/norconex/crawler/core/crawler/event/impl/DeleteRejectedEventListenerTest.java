/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core.crawler.event.impl;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.text.TextMatcher;

class DeleteRejectedEventListenerTest {
    @TempDir
    private Path tempDir;

    //TODO migrate this:
    /*
    @Test
    void testDeleteRejectedEventListener() {

        var crawlSession = CoreStubber.crawlSession(tempDir,
                "mock:delete1", "mock:keep2", "mock:delete3", "mock:delete4");
        var crawlerCfg = TestUtil.getFirstCrawlerConfig(crawlSession);
        crawlerCfg.addEventListener(new DeleteRejectedEventListener());

        var f = Configurable.configure(new GenericReferenceFilter(), cfg -> cfg
                .setValueMatcher(TextMatcher.wildcard("mock:delete*"))
                .setOnMatch(OnMatch.EXCLUDE));

//        crawlerCfg.getImporterConfig().setPreParseConsumer(
//                HandlerConsumerAdapter.fromHandlers(f));
        crawlerCfg.setReferenceFilters(List.of(f));

        crawlSession.start();

        var mem = TestUtil.getFirstMemoryCommitter(crawlSession);

        assertThat(mem.getRequestCount()).isEqualTo(4);

        assertThat(mem.getUpsertCount()).isEqualTo(1);
        assertThat(mem.getDeleteCount()).isEqualTo(3);
        assertThat(mem.getDeleteRequests())
            .map(DeleteRequest::getReference)
            .containsExactly("mock:delete1", "mock:delete3", "mock:delete4");
    }
    */

    @Test
    void testWriteRead() {
        var listener = new DeleteRejectedEventListener();
        listener.getConfiguration().setEventMatcher(
                TextMatcher.basic("deleteme"));
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(listener));
    }
}
