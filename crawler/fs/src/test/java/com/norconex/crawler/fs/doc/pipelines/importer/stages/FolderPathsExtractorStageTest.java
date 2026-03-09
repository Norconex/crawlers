/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.fs.doc.pipelines.importer.stages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipeline;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchDirectiveSupport;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.fs.doc.FsCrawlEntry;
import com.norconex.crawler.fs.fetch.FolderPathsFetchResponse;
import com.norconex.crawler.fs.fetch.FsPath;
import com.norconex.importer.doc.Doc;

@Timeout(30)
class FolderPathsExtractorStageTest {

    /**
     * Builds an {@link ImporterPipelineContext} backed by mocks,
     * wiring together CrawlSession → CrawlContext → CrawlConfig/Fetcher/
     * DocPipelines, and a CrawlDocContext wrapping the given entry.
     */
    private ImporterPipelineContext buildCtx(
            FsCrawlEntry entry,
            Fetcher fetcher,
            QueuePipeline queuePipeline) {

        var config = new CrawlConfig()
                .setDocumentFetchSupport(FetchDirectiveSupport.REQUIRED);

        var docPipelines = CrawlDocPipelines.builder()
                .queuePipeline(queuePipeline)
                .build();

        var crawlContext = mock(CrawlContext.class);
        when(crawlContext.getCrawlConfig()).thenReturn(config);
        when(crawlContext.getFetcher()).thenReturn(fetcher);
        when(crawlContext.getDocPipelines()).thenReturn(docPipelines);
        when(crawlContext.createCrawlEntry(any()))
                .thenAnswer(inv -> new FsCrawlEntry(
                        inv.getArgument(0, String.class)));

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);

        var docContext = CrawlDocContext.builder()
                .doc(new Doc(entry.getReference()))
                .currentCrawlEntry(entry)
                .build();

        return new ImporterPipelineContext(session, docContext);
    }

    @Test
    void testFetchExceptionWrapped() {
        var entry = new FsCrawlEntry("file:///some/folder");
        entry.setFolder(true);

        var fetcher = mock(Fetcher.class);
        try {
            when(fetcher.fetch(any()))
                    .thenThrow(new FetchException("blah"));
        } catch (FetchException e) {
            throw new AssertionError("Unexpected exception in mock setup", e);
        }

        var ctx = buildCtx(
                entry,
                fetcher,
                mock(QueuePipeline.class));

        assertThatExceptionOfType(CrawlerException.class)
                .isThrownBy(() -> //NOSONAR
                new FolderPathsExtractorStage(
                        FetchDirective.DOCUMENT).test(ctx))
                .withMessageContaining("Could not fetch child paths of:");
    }

    @Test
    void testFolderChildPathsQueued() {
        var entry = new FsCrawlEntry("file:///some/folder");
        entry.setFolder(true);

        var child1 = new FsPath("file:///some/folder/child1.txt");
        child1.setFile(true);
        var child2 = new FsPath("file:///some/folder/sub");
        child2.setFolder(true);

        var mockResponse = mock(FolderPathsFetchResponse.class);
        when(mockResponse.getChildPaths())
                .thenReturn(Set.of(child1, child2));

        var fetcher = mock(Fetcher.class);
        try {
            when(fetcher.fetch(any())).thenReturn(mockResponse);
        } catch (FetchException e) {
            throw new AssertionError("Unexpected exception in mock setup", e);
        }

        // A no-op queue pipeline — we only assert on the return value
        var queuePipeline = mock(QueuePipeline.class);

        var ctx = buildCtx(entry, fetcher, queuePipeline);

        // A folder-only entry returns false (no file content to process)
        boolean result = new FolderPathsExtractorStage(
                FetchDirective.DOCUMENT).test(ctx);
        assertThat(result).isFalse();
    }

    @Test
    void testFolderAndFileEntryReturnsContinue() {
        // An entry that is both a folder and a file should return true
        var entry = new FsCrawlEntry("file:///some/folderfile");
        entry.setFolder(true);
        entry.setFile(true);

        var mockResponse = mock(FolderPathsFetchResponse.class);
        when(mockResponse.getChildPaths()).thenReturn(Set.of());

        var fetcher = mock(Fetcher.class);
        try {
            when(fetcher.fetch(any())).thenReturn(mockResponse);
        } catch (FetchException e) {
            throw new AssertionError("Unexpected exception in mock setup", e);
        }

        var ctx = buildCtx(entry, fetcher, mock(QueuePipeline.class));

        boolean result = new FolderPathsExtractorStage(
                FetchDirective.DOCUMENT).test(ctx);
        assertThat(result).isTrue();
    }

    @Test
    void testNonFolderEntrySkipsAndReturnsFile() {
        // A file-only entry must skip folder logic and return isFile()
        var entry = new FsCrawlEntry("file:///some/file.txt");
        entry.setFile(true);

        var fetcher = mock(Fetcher.class);

        var ctx = buildCtx(entry, fetcher, mock(QueuePipeline.class));

        boolean result = new FolderPathsExtractorStage(
                FetchDirective.DOCUMENT).test(ctx);
        assertThat(result).isTrue();
    }
}
