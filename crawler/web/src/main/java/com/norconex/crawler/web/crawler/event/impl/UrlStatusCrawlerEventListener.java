/* Copyright 2015-2023 Norconex Inc.
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
package com.norconex.crawler.web.crawler.event.impl;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionEvent;
import com.norconex.crawler.core.session.CrawlSessionException;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.link.impl.HtmlLinkExtractor;
import com.norconex.crawler.web.link.impl.TikaLinkExtractor;

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
 * By default one generated report is created for each crawler, stored
 * in crawler-specific directories under the collector working directory.
 * The collector working directory can be overwritten using
 * {@link UrlStatusCrawlerEventListenerConfig#setOutputDir(Path)}.
 * If {@link UrlStatusCrawlerEventListenerConfig#isCombined()}
 * is <code>true</code>, status from all crawlers
 * defined will be written to a unique file in the collector working directory.
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
 * <h3>Filter which crawler to record URL statuses</h3>
 * <p>
 * By default all crawlers will have their URL fetch statuses recorded when
 * using this event listener.  To only do so for some crawlers, you can
 * use {@link UrlStatusCrawlerEventListenerConfig#setCrawlerIds(List)} to
 * identify them.
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
 * {@nx.xml.usage
 * <listener
 *     class="com.norconex.crawler.web.crawler.event.impl.UrlStatusCrawlerEventListener">
 *   <statusCodes>(CSV list of status codes)</statusCodes>
 *   <crawlerIds>
 *     <!-- repeat as needed -->
 *     <id>(existing crawler ID)</id>
 *   </crawlerIds>
 *   <outputDir>(path to a directory of your choice)</outputDir>
 *   <fileNamePrefix>(report file name prefix)</fileNamePrefix>
 *   <combined>[false|true]</combined>
 *   <timestamped>[false|true]</timestamped>
 * </listener>
 * }
 *
 * {@nx.xml.example
 * <listener class="UrlStatusCrawlerEventListener">
 *   <statusCodes>404</statusCodes>
 *   <outputDir>/report/path/</outputDir>
 *   <fileNamePrefix>brokenLinks</fileNamePrefix>
 * </listener>
 * }
 * <p>
 * The above example will generate a broken links report by recording
 * 404 status codes (from HTTP response).
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
    private final Map<String, CSVPrinter> csvPrinters = new HashMap<>();

    @Override
    public void accept(Event event) {
        if (event.is(CrawlSessionEvent.CRAWLSESSION_RUN_BEGIN)) {
            init((CrawlSession) event.getSource());
            return;
        }
        if (event.is(CrawlSessionEvent.CRAWLSESSION_RUN_END)) {
            for (CSVPrinter csv : csvPrinters.values()) {
                try {
                    csv.close(true);
                } catch (IOException e) {
                    LOG.error("Could not close CSVPrinter.", e);
                }
            }
            return;
        }

        if (!(event instanceof CrawlerEvent ce)) {
            return;
        }

        if ((ce.getSubject() instanceof HttpFetchResponse response)
                && (parsedCodes.isEmpty()
                || parsedCodes.contains(response.getStatusCode()))) {
            var csv = csvPrinters.get(configuration.isCombined()
                    ? null : ce.getSource().getId());
            if (csv != null) {
                var crawlRef = (WebDocRecord) ce.getCrawlDocRecord();
                Object[] csvRecord = {
                        trimToEmpty(crawlRef.getReferrerReference()),
                        trimToEmpty(crawlRef.getReference()),
                        response.getStatusCode(),
                        response.getReasonPhrase()
                };
                printCSVRecord(csv, csvRecord);
            }
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

    private void init(CrawlSession collector) {

        var baseDir = getBaseDir(collector);
        var timestamp = "";
        if (configuration.isTimestamped()) {
            timestamp = LocalDateTime.now().truncatedTo(
                    ChronoUnit.MILLIS).toString();
            timestamp = timestamp.replace(':', '-');
        }

        // if combined == true, get using null to hashmap.
        if (configuration.isCombined()) {
            csvPrinters.put(null,
                    createCSVPrinter(baseDir, collector.getId(), timestamp));
        } else {
            for (Crawler crawler : collector.getCrawlers()) {
                var id = crawler.getId();
                if (configuration.getCrawlerIds().isEmpty()
                        || configuration.getCrawlerIds().contains(id)) {
                    csvPrinters.put(id, createCSVPrinter(
                            baseDir, id, timestamp));
                }
            }
        }

        // Parse status codes
        if (StringUtils.isBlank(configuration.getStatusCodes())) {
            parsedCodes.clear();
            return;
        }

        Stream.of(StringUtils.split(configuration.getStatusCodes(),  ','))
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
                        "Invalid statusCode range: " + range
                      + ". Start value must be higher than end value.");
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

    private Path getBaseDir(CrawlSession collector) {
        if (configuration.getOutputDir() == null) {
            return collector.getWorkDir();
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
            var csv = new CSVPrinter(Files.newBufferedWriter(file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE),
                    CSVFormat.EXCEL);
            csv.printRecord("Referrer", "URL", "Status", "Reason");
            return csv;
        } catch (IOException e) {
            throw new CrawlSessionException(
                    "Cannot create output directory for file: " + file, e);
        }
    }

    private int toInt(String num) {
        try {
            return Integer.parseInt(num.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The statusCodes attribute "
                    + "can only contain valid numbers. This number is invalid: "
                    + num);
        }
    }
}
