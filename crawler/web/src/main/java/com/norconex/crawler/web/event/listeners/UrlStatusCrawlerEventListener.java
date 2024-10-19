/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.web.event.listeners;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.tasks.CrawlerTaskContext;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.operations.link.impl.HtmlLinkExtractor;
import com.norconex.crawler.web.doc.operations.link.impl.TikaLinkExtractor;
import com.norconex.crawler.web.fetch.HttpFetchResponse;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Store on file all URLs that were "fetched", along with their HTTP response
 * code. Useful for reporting purposes (e.g. finding broken links). A short
 * summary of all HTTP status codes can be found
 * <a href="http://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml">here</a>.
 * </p>
 *
 * <h3>Filter by status codes</h3>
 * <p>
 * By default, the status of all fetched URLs are stored by this listener,
 * regardless what were those statuses.  This can generate very lengthy reports
 * on large crawls. If you are only interested in certain status codes, you can
 * listen only for those using the
 * {@link UrlStatusCrawlerEventListenerConfig#setStatusCodes(String)} method
 * or XML configuration equivalent. You specify the codes you want to listen
 * for as coma-separated values. Ranges are also supported: specify two range
 * values (both inclusive) separated by an hyphen.  For instance, if you want
 * to store all "bad" URLs, you can quickly specify all codes except
 * 200 (OK) this way:
 * </p>
 * <pre>100-199,201-599</pre>
 *
 * <h3>Output location</h3>
 * <p>
 * By default the generated report is created under the collector working
 * directory.
 * The collector working directory can be overwritten using
 * {@link UrlStatusCrawlerEventListenerConfig#setOutputDir(Path)}.
 * </p>
 *
 * <h3>File naming</h3>
 * <p>
 * By default, the file generated will use this naming pattern:
 * </p>
 * <pre>
 *   urlstatuses-[timestamp].csv
 * </pre>
 * <p>
 * The filename prefix can be changed from "urlstatuses-" to anything else
 * using {@link UrlStatusCrawlerEventListenerConfig#setFileNamePrefix(String)}.
 * </p>
 *
 * <h3>Referring/parent URLs and custom link extractor</h3>
 * <p>
 * To capture the referring pages you have to use a link extractor that
 * extracts referrer information.  The default link extractor
 * {@link HtmlLinkExtractor} properly extracts this information.  Same with
 * {@link TikaLinkExtractor}.  This is only a consideration when
 * using a custom link extractor.
 * </p>
 *
 * @since 2.2.0
 */
@EqualsAndHashCode
@ToString
@Slf4j
public class UrlStatusCrawlerEventListener implements
        EventListener<Event>,
        Configurable<UrlStatusCrawlerEventListenerConfig> {

    @Getter
    private final UrlStatusCrawlerEventListenerConfig configuration =
            new UrlStatusCrawlerEventListenerConfig();

    // variables set when crawler starts/resumes
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    private final List<Integer> parsedCodes = new ArrayList<>();
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    private CSVPrinter csvPrinter;

    @Override
    public void accept(Event event) {
        if (event.is(CrawlerEvent.CRAWLER_RUN_BEGIN)) {
            init((CrawlerTaskContext) event.getSource());
            return;
        }
        if (event.is(CrawlerEvent.CRAWLER_RUN_END)) {
            try {
                csvPrinter.close();
            } catch (IOException e) {
                throw new CrawlerException("Could not close CSV stream.", e);
            }
            return;
        }

        if (!(event instanceof CrawlerEvent ce)) {
            return;
        }

        if (((ce.getSubject() instanceof HttpFetchResponse response)
                && (parsedCodes.isEmpty()
                        || parsedCodes.contains(response.getStatusCode())))
                && (csvPrinter != null)) {
            var crawlRef = (WebCrawlDocContext) ce.getDocContext();
            Object[] csvRecord = {
                    trimToEmpty(crawlRef.getReferrerReference()),
                    trimToEmpty(crawlRef.getReference()),
                    response.getStatusCode(),
                    response.getReasonPhrase()
            };
            printCSVRecord(csvPrinter, csvRecord);
        }
    }

    private synchronized void printCSVRecord(
            CSVPrinter csv, Object[] csvRecord) {
        try {
            csv.printRecord(csvRecord);
        } catch (IOException e) {
            LOG.error("Could not write CSV record: {}", csvRecord, e);
        }
    }

    private void init(CrawlerTaskContext crawler) {

        var baseDir = getBaseDir(crawler);
        var timestamp = "";
        if (configuration.isTimestamped()) {
            timestamp = LocalDateTime.now().truncatedTo(
                    ChronoUnit.MILLIS).toString();
            timestamp = timestamp.replace(':', '-');
        }

        // if combined == true, get using null to hashmap.
        csvPrinter = createCSVPrinter(baseDir, crawler.getId(), timestamp);

        // Parse status codes
        if (StringUtils.isBlank(configuration.getStatusCodes())) {
            parsedCodes.clear();
            return;
        }

        Stream.of(StringUtils.split(configuration.getStatusCodes(), ','))
                .map(String::trim)
                .forEach(range -> resolveStatusCodeRange(parsedCodes, range));
    }

    private void resolveStatusCodeRange(
            List<Integer> parsedCodes, String range) {
        var endPoints = StringUtils.split(range.trim(), '-');
        if (endPoints.length == 1) {
            parsedCodes.add(toInt(endPoints[0]));
        } else if (endPoints.length == 2) {
            var start = toInt(endPoints[0]);
            var end = toInt(endPoints[1]);
            if (start >= end) {
                throw new IllegalArgumentException(
                        ("Invalid statusCode range: %s. Start value must be "
                                + "higher than end value.").formatted(range));
            }
            while (start <= end) {
                parsedCodes.add(start);
                start++;
            }
        } else {
            throw new IllegalArgumentException(
                    "Invalid statusCode range: " + range);
        }
    }

    private Path getBaseDir(CrawlerTaskContext crawler) {
        if (configuration.getOutputDir() == null) {
            return crawler.getWorkDir();
        }
        return configuration.getOutputDir();
    }

    private CSVPrinter createCSVPrinter(Path dir, String id, String suffix) {
        var prefix = StringUtils.defaultString(
                configuration.getFileNamePrefix());
        var safeSuffix = "";
        if (StringUtils.isNotBlank(suffix)) {
            safeSuffix = "-" + suffix;
        }
        var file = dir.resolve(
                prefix + FileUtil.toSafeFileName(id) + safeSuffix + ".csv");
        try {
            Files.createDirectories(dir);
            var csv = new CSVPrinter(
                    Files.newBufferedWriter(
                            file,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE),
                    CSVFormat.EXCEL);
            csv.printRecord("Referrer", "URL", "Status", "Reason");
            return csv;
        } catch (IOException e) {
            throw new CrawlerException(
                    "Cannot create output directory for file: " + file, e);
        }
    }

    private int toInt(String num) {
        try {
            return Integer.parseInt(num.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "The statusCodes attribute can only contain valid numbers. "
                            + "This number is invalid: " + num);
        }
    }
}
