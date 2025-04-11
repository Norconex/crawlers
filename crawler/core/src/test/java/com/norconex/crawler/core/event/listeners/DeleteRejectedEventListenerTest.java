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
package com.norconex.crawler.core.event.listeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;

class DeleteRejectedEventListenerTest {

    @CrawlTest(
        focus = Focus.CRAWL,
        config = """
            startReferences:
              - "mock:delete1"
              - "mock:keep2"
              - "mock:delete3"
              - "mock:delete4"
            eventListeners:
              - class: DeleteRejectedEventListener
            importer:
              handlers:
                - if:
                    condition:
                      class: ReferenceCondition
                      valueMatcher:
                        method: WILDCARD
                        pattern: "mock:delete*"
                    then:
                      - handler:
                          class: Reject
            """
    )
    void testDeleteRejectedEventListener(
            CrawlerContext crawlerContext, MemoryCommitter mem) {

        assertThat(mem.getRequestCount()).isEqualTo(4);

        assertThat(mem.getUpsertCount()).isEqualTo(1);
        assertThat(mem.getDeleteCount()).isEqualTo(3);
        assertThat(mem.getDeleteRequests())
                .map(DeleteRequest::getReference)
                .containsExactlyInAnyOrder(
                        "mock:delete1",
                        "mock:delete3",
                        "mock:delete4");
    }

    @Test
    void testWriteRead() {
        var listener = new DeleteRejectedEventListener();
        listener.getConfiguration().setEventMatcher(
                TextMatcher.basic("deleteme"));
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(listener));
    }
}
