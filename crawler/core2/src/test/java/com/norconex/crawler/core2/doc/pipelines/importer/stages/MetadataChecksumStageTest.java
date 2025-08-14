/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.core2.doc.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core2.doc.CrawlDocMetaConstants;
import com.norconex.crawler.core2.doc.operations.checksum.impl.GenericMetadataChecksummer;
import com.norconex.crawler.core2.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core2.fetch.FetchDirective;
import com.norconex.crawler.core2.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core2.junit.CrawlTest;
import com.norconex.crawler.core2.junit.CrawlTest.Focus;
import com.norconex.crawler.core2.mocks.crawler.MockCrawlerBuilder;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.stubs.CrawlDocContextStubber;

class MetadataChecksumStageTest {

    @TempDir
    private Path tempDir;

    @CrawlTest(focus = Focus.SESSION)
    void testMetadataChecksumStage(CrawlSession session) {
        var docCtx = CrawlDocContextStubber.fresh(
                "ref", "content", "myfield", "somevalue");
        session.getCrawlContext().getCrawlConfig().setMetadataFetchSupport(
                FetchDirectiveSupport.REQUIRED);

        // without a checksummer
        var ctx = new ImporterPipelineContext(session, docCtx);
        new MetadataChecksumStage(FetchDirective.METADATA).test(ctx);
        assertThat(docCtx.getDoc().getMetadata().getString(
                CrawlDocMetaConstants.CHECKSUM_METADATA)).isNull();

        // with a checksummer
        var checksummer = new GenericMetadataChecksummer();
        checksummer.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("myfield"))
                .setKeep(true);
        session.getCrawlContext().getCrawlConfig()
                .setMetadataChecksummer(checksummer);
        new MetadataChecksumStage(FetchDirective.METADATA).test(ctx);
        assertThat(docCtx.getDoc().getMetadata().getString(
                CrawlDocMetaConstants.CHECKSUM_METADATA)).isEqualTo(
                        "myfield=somevalue;");
    }

    @Test
    void testRejectedUnmodified() {

        var checksummer = new GenericMetadataChecksummer();
        checksummer
                .getConfiguration()
                .setFieldMatcher(TextMatcher.wildcard("*"));

        var meta = new Properties();
        meta.add("key", "value");

        new MockCrawlerBuilder(tempDir)
                .configModifier(cfg -> {
                    cfg.setMetadataFetchSupport(FetchDirectiveSupport.REQUIRED)
                            .setMetadataChecksummer(checksummer);
                })
                .build()
                .withCrawlSession(session -> {
                    var docCtx = CrawlDocContextStubber.incremental(
                            "ref", "content", "key", "value");
                    docCtx.getCurrentCrawlEntry().setMetaChecksum(
                            checksummer.createMetadataChecksum(meta));

                    docCtx.getPreviousCrawlEntry().setMetaChecksum(
                            checksummer.createMetadataChecksum(meta));

                    var ctx = new ImporterPipelineContext(session, docCtx);

                    var stage =
                            new MetadataChecksumStage(FetchDirective.METADATA);
                    assertThat(stage.test(ctx)).isFalse();
                });

    }

}
