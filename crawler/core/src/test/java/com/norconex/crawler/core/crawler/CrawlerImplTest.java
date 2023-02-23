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
package com.norconex.crawler.core.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.Stubber;
import com.norconex.crawler.core.crawler.CrawlerImpl.CrawlerImplBuilder;
import com.norconex.crawler.core.crawler.CrawlerImpl.QueueInitContext;
import com.norconex.crawler.core.doc.CrawlDocRecord;

class CrawlerImplTest {

    @TempDir
    private Path tempDir;

    @Test
    void testQueueInitContext() {
        var docRec1 = Stubber.crawlDocRecord("ref1");
        var docRec2 = Stubber.crawlDocRecordRandom();

        CrawlDocRecord[] expected = {docRec1, docRec2};

        List<CrawlDocRecord> actual = new ArrayList<>();
        var qic = new QueueInitContext(
                Stubber.crawler(tempDir),
                false,
                docRec -> actual.add(docRec));

        qic.queue(docRec1);
        qic.queue(docRec2);

        assertThat(actual).containsExactly(expected);
    }

    @Test
    void testConstructor() {
        assertThatNoException().isThrownBy(CrawlerImplBuilder::new);
    }

    @Test
    void testErrors() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> CrawlerImpl.builder() //NOSONAR
                .committerPipeline(null)
                .fetcherProvider(req -> null)
                .importerPipeline(ctx -> null)
                .queuePipeline(ctx -> {})
                .build()
        );
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> CrawlerImpl.builder()  //NOSONAR
                .committerPipeline(ctx -> {})
                .fetcherProvider(null)
                .importerPipeline(ctx -> null)
                .queuePipeline(ctx -> {})
                .build()
        );
    }
}
