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
package com.norconex.crawler.fs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.fs.crawler.FsCrawlerConfig;
import com.norconex.crawler.fs.fetch.FileFetcher;
import com.norconex.importer.doc.Doc;

import lombok.NonNull;

public final class FsTestUtil {

    //MAYBE: maybe move some of the common test classes/methods to core
    // and make it a usable test artifact?

    private FsTestUtil() {}

    /**
     * Gets the {@link MemoryCommitter} from first committer of the first
     * crawler from a crawl session (assuming the first committer is
     * a {@link MemoryCommitter}).  If that committer does not
     * exists or is not a memory committer, an exception is thrown.
     * @param crawlSession crawl session
     * @return Memory committer
     */
    public static MemoryCommitter getFirstMemoryCommitter(
            @NonNull CrawlSession crawlSession) {
        return (MemoryCommitter) getFirstCrawlerConfig(
                crawlSession).getCommitters().get(0);
    }
    public static MemoryCommitter getFirstMemoryCommitter(
            @NonNull Crawler crawler) {
        return (MemoryCommitter)
                crawler.getCrawlerConfig().getCommitters().get(0);
    }
    public static Crawler getFirstCrawler(
            @NonNull CrawlSession crawlSession) {
        if (!crawlSession.getCrawlers().isEmpty()) {
            return crawlSession.getCrawlers().get(0);
        }
        return null;
    }
    public static FsCrawlerConfig getFirstCrawlerConfig(
            @NonNull CrawlSession crawlSession) {
        return getFirstCrawlerConfig(crawlSession.getCrawlSessionConfig());
    }
    public static FsCrawlerConfig getFirstCrawlerConfig(
            @NonNull CrawlSessionConfig crawlSessionConfig) {
        return (FsCrawlerConfig) crawlSessionConfig.getCrawlerConfigs().get(0);
    }
    public static FileFetcher getFirstHttpFetcher(
            @NonNull CrawlSession crawlSession) {
        return (FileFetcher) getFirstCrawlerConfig(
                crawlSession.getCrawlSessionConfig()).getFetchers().get(0);
    }

    public static Optional<UpsertRequest> getUpsertRequest(
            @NonNull MemoryCommitter mem, @NonNull String ref) {
        return mem.getUpsertRequests().stream()
                .filter(m -> ref.equals(m.getReference()))
                .findFirst();
    }
    public static String getUpsertRequestMeta(
            @NonNull MemoryCommitter mem,
            @NonNull String ref,
            @NonNull String fieldName) {
        return getUpsertRequest(mem, ref)
                .map(req -> req.getMetadata().getString(fieldName))
                .orElse(null);
    }
    public static String getUpsertRequestContent(
            @NonNull MemoryCommitter mem, @NonNull String ref) {
        return getUpsertRequest(mem, ref)
                .map(FsTestUtil::docText)
                .orElse(null);
    }
    public static Optional<DeleteRequest> getDeleteRequest(
            @NonNull MemoryCommitter mem, @NonNull String ref) {
        return mem.getDeleteRequests().stream()
                .filter(m -> ref.equals(m.getReference()))
                .findFirst();
    }

    public static Set<String> sortedRequestReferences(MemoryCommitter c) {
        return c.getAllRequests().stream()
                .map(CommitterRequest::getReference)
                .collect(Collectors.toCollection(TreeSet::new));
    }
    public static Set<String> sortedUpsertReferences(MemoryCommitter c) {
        return c.getUpsertRequests().stream()
                .map(UpsertRequest::getReference)
                .collect(Collectors.toCollection(TreeSet::new));
    }
    public static Set<String> sortedDeleteReferences(MemoryCommitter c) {
        return c.getDeleteRequests().stream()
                .map(DeleteRequest::getReference)
                .collect(Collectors.toCollection(TreeSet::new));
    }
    public static String lastSortedRequestReference(MemoryCommitter c) {
        return sortedRequestReferences(c).stream()
            .reduce((first, second) -> second)
            .orElse(null);
    }
    public static String lastSortedUpsertReference(MemoryCommitter c) {
        return sortedUpsertReferences(c).stream()
                .reduce((first, second) -> second)
                .orElse(null);
    }
    public static String lastSortedDeleteReference(MemoryCommitter c) {
        return sortedDeleteReferences(c).stream()
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public static ZonedDateTime daysAgo(int days) {
        return ZonedDateTime.now().minusDays(days).withNano(0);
    }
    public static String daysAgoRFC(int days) {
        return rfcFormat(daysAgo(days));
    }
    public static String rfcFormat(ZonedDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    public static String docText(Doc doc) {
        return toString(doc.getInputStream());
    }
    public static String docText(UpsertRequest doc) {
        return toString(doc.getContent());
    }
    public static String toString(InputStream is) {
        try {
            return IOUtils.toString(is, UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    public static String resourceAsString(String resourcePath) {
        return toString(FsTestUtil.class.getResourceAsStream(resourcePath));
    }

    public static int freePort() {
        try (var serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
