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
package com.norconex.crawler.fs.fetch.impl;

import static com.norconex.crawler.fs.FsTestUtil.getUpsertRequestContent;
import static com.norconex.crawler.fs.FsTestUtil.getUpsertRequestMeta;
import static com.norconex.crawler.fs.doc.FsDocMetadata.FILE_SIZE;
import static com.norconex.importer.doc.DocMetadata.CONTENT_ENCODING;
import static com.norconex.importer.doc.DocMetadata.CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.committer.core.UpsertRequest;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.crawler.fs.TestFsCrawlSession;
import com.norconex.crawler.fs.doc.FsDocMetadata;
import com.norconex.crawler.fs.fetch.FileFetcher;

abstract class AbstractFileFetcherTest {

    private final FileFetcher fetcher;

    public AbstractFileFetcherTest(FileFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Test
    void testFetchFiles() {
        var basePath = getStartPath();
        var mem = TestFsCrawlSession.forStartPaths(basePath)
            .crawlerSetup(cfg -> {
                cfg.setFileFetchers(List.of(fetcher));
            })
            .crawl();

        assertThat(mem.getUpsertCount()).isEqualTo(8);
        assertThat(mem.getUpsertRequests())
            .map(UpsertRequest::getReference)
            .containsExactlyInAnyOrder(
                    basePath + "/bye.txt",
                    basePath + "/embedded.zip",
                    basePath + "/hello.txt",
                    basePath + "/UTF-8.txt",
                    basePath + "/windows-1252.txt",
                    basePath + "/imgs/160x120.png",
                    basePath + "/imgs/320x240.png",
                    basePath + "/pdfs/plain.pdf");

        // Assert content
        assertThat(getUpsertRequestContent(
                mem, basePath + "/bye.txt")).contains("Bye World!");
        assertThat(getUpsertRequestContent(
                mem, basePath + "/pdfs/plain.pdf"))
                        .contains("Hey Norconex, this is a test.");

        // Assert char encoding
        assertThat(getUpsertRequestMeta(
                mem, basePath + "/UTF-8.txt", CONTENT_ENCODING))
                        .isEqualTo("UTF-8");
        assertThat(getUpsertRequestMeta(
                mem, basePath + "/windows-1252.txt", CONTENT_ENCODING))
                        .isEqualTo("windows-1252");

        // Assert content type
        assertThat(getUpsertRequestMeta(
                mem, basePath + "/UTF-8.txt", CONTENT_TYPE))
                        .isEqualTo(ContentType.TEXT.toString());
        assertThat(getUpsertRequestMeta(
                mem, basePath + "/pdfs/plain.pdf", CONTENT_TYPE))
                        .isEqualTo(ContentType.PDF.toString());

        // Assert file size
        assertThat(getUpsertRequestMeta(
                mem, basePath + "/imgs/320x240.png", FILE_SIZE))
                        .isEqualTo("1853");
        assertThat(getUpsertRequestMeta(
                mem, basePath + "/pdfs/plain.pdf", FILE_SIZE))
                        .isEqualTo("15987");

        // Assert last modified (UTC)
        assertThat(getUpsertRequestMeta(
                mem, basePath + "/imgs/160x120.png", FsDocMetadata.LAST_MODIFIED))
                        .isEqualTo(isoLocalToEpoch("2023-03-15T07:25:00"));
        assertThat(getUpsertRequestMeta(
                mem, basePath + "/embedded.zip", FsDocMetadata.LAST_MODIFIED))
                        .isEqualTo(isoLocalToEpoch("2022-08-29T04:00:00"));

//mem.getAllRequests().forEach(req -> {
//    System.err.println("FILE: " + req.getReference());
//    System.err.println("META: \n" + req.getMetadata());
//});

        // Assert ACLs

        //TODO extract ACL, possibly making it a flag if costly?
    }

    abstract String getStartPath();

    private static String isoLocalToEpoch(String isoLocal) {
        return LocalDateTime
                .from(DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(isoLocal))
                .atZone(ZoneOffset.UTC)
                .toEpochSecond() + "000";
    }
}
