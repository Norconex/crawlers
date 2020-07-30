/* Copyright 2015-2020 Norconex Inc.
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
package com.norconex.collector.http.crawler.event.impl;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.HashCodeExclude;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringExclude;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorEvent;
import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.link.impl.HtmlLinkExtractor;
import com.norconex.collector.http.link.impl.TikaLinkExtractor;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.IEventListener;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

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
 * listen only for those using the {@link #setStatusCodes(String)} method
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
 * {@link #setOutputDir(Path)}.
 * If {@link #isCombined()} is <code>true</code>, status from all crawlers
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
 * using {@link #setFileNamePrefix(String)}.
 * </p>
 *
 * <h3>Filter which crawler to record URL statuses</h3>
 * <p>
 * By default all crawlers will have their URL fetch statuses recorded when
 * using this event listener.  To only do so for some crawlers, you can
 * use {@link #setCrawlerIds(List)} to identify them.
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
 *     class="com.norconex.collector.http.crawler.event.impl.URLStatusCrawlerEventListener">
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
 * <listener
 *     class="com.norconex.collector.http.crawler.event.impl.URLStatusCrawlerEventListener">
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
 * @author Pascal Essiembre
 * @since 2.2.0
 */
public class URLStatusCrawlerEventListener
        implements IEventListener<Event<?>>, IXMLConfigurable {

    private static final Logger LOG =
            LoggerFactory.getLogger(URLStatusCrawlerEventListener.class);

    public static final String DEFAULT_FILENAME_PREFIX = "urlstatuses-";

    private String statusCodes;
    private Path outputDir;
    private String fileNamePrefix = DEFAULT_FILENAME_PREFIX;
    private final List<String> crawlerIds = new ArrayList<>();
    private boolean combined;
    private boolean timestamped;

    // variables set when crawler starts/resumes
    @EqualsExclude
    @HashCodeExclude
    @ToStringExclude
    private final List<Integer> parsedCodes = new ArrayList<>();
    @EqualsExclude
    @HashCodeExclude
    @ToStringExclude
    private final Map<String, CSVPrinter> csvPrinters = new HashMap<>();


    /**
     * Gets the status codes to listen for. Default is <code>null</code>
     * (listens for all status codes).
     * @return status codes
     */
    public String getStatusCodes() {
        return statusCodes;
    }
    /**
     * Sets a coma-separated list of status codes to listen to.
     * See class documentation for how to specify code ranges.
     * @param statusCodes the status codes to listen for
     */
    public void setStatusCodes(String statusCodes) {
        this.statusCodes = statusCodes;
    }

    /**
     * Gets the local directory where this listener report will be written.
     * Default uses the collector working directory.
     * @return directory path
     */
    public Path getOutputDir() {
        return outputDir;
    }
    /**
     * Sets the local directory where this listener report will be written.
     * @param outputDir directory path
     */
    public void setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Gets the generated report file name prefix. See class documentation
     * for default prefix.
     * @return file name prefix
     */
    public String getFileNamePrefix() {
        return fileNamePrefix;
    }
    /**
     * Sets the generated report file name prefix.
     * @param fileNamePrefix file name prefix
     */
    public void setFileNamePrefix(String fileNamePrefix) {
        this.fileNamePrefix = fileNamePrefix;
    }

    /**
     * Gets whether to add a timestamp to the file name, to ensure
     * a new one is created with each run.
     * @return <code>true</code> if timestamped
     * @since 3.0.0
     */
    public boolean isTimestamped() {
        return timestamped;
    }
    /**
     * Sets whether to add a timestamp to the file name, to ensure
     * a new one is created with each run.
     * @param timestamped <code>true</code> if timestamped
     * @since 3.0.0
     */
    public void setTimestamped(boolean timestamped) {
        this.timestamped = timestamped;
    }

    public boolean isCombined() {
        return combined;
    }
    public void setCombined(boolean combined) {
        this.combined = combined;
    }
    public List<String> getCrawlerIds() {
        return Collections.unmodifiableList(crawlerIds);
    }
    public void setCrawlerIds(List<String> crawlerIds) {
        CollectionUtil.setAll(this.crawlerIds, crawlerIds);
    }

    @Override
    public void accept(Event<?> event) {
        if (event.is(CollectorEvent.COLLECTOR_RUN_BEGIN)) {
            init((HttpCollector) event.getSource());
            return;
        }
        if (event.is(CollectorEvent.COLLECTOR_RUN_END)) {
            for (CSVPrinter csv : csvPrinters.values()) {
                try {
                    csv.close(true);
                } catch (IOException e) {
                    LOG.error("Could not close CSVPrinter.", e);
                }
            }
            return;
        }

        if (!(event instanceof CrawlerEvent)) {
            return;
        }

        CrawlerEvent<?> ce = (CrawlerEvent<?>) event;
        if (ce.getSubject() instanceof IHttpFetchResponse) {
            IHttpFetchResponse response = (IHttpFetchResponse) ce.getSubject();
            if (parsedCodes.isEmpty()
                    || parsedCodes.contains(response.getStatusCode())) {
                CSVPrinter csv = csvPrinters.get(combined
                        ? null : ((HttpCrawler) ce.getSource()).getId());
                if (csv != null) {
                    HttpDocInfo crawlRef =
                            (HttpDocInfo) ce.getCrawlReference();
                    Object[] record = new Object[] {
                            trimToEmpty(crawlRef.getReferrerReference()),
                            trimToEmpty(crawlRef.getReference()),
                            response.getStatusCode(),
                            response.getReasonPhrase()
                    };
                    try {
                        csv.printRecord(record);
                    } catch (IOException e) {
                        LOG.error("Could not write record: {}", record, e);
                    }
                }
            }
        }
    }

    private void init(HttpCollector collector) {

        Path baseDir = getBaseDir(collector);
        String timestamp = "";
        if (isTimestamped()) {
            timestamp = LocalDateTime.now().truncatedTo(
                    ChronoUnit.MILLIS).toString();
            timestamp = timestamp.replace(':', '-');
        }

        // if combined == true, get using null to hashmap.
        if (combined) {
            csvPrinters.put(null,
                    createCSVPrinter(baseDir, collector.getId(), timestamp));
        } else {
            for (Crawler crawler : collector.getCrawlers()) {
                String id = crawler.getId();
                if (crawlerIds.isEmpty() || crawlerIds.contains(id)) {
                    csvPrinters.put(id, createCSVPrinter(baseDir, id, timestamp));
                }
            }
        }

        // Parse status codes
        if (StringUtils.isBlank(statusCodes)) {
            parsedCodes.clear();
            return;
        }
        String[] ranges = statusCodes.split("\\s*,\\s*");
        for (String range : ranges) {
            String[] endPoints = range.split("\\s*-\\s*");
            if (endPoints.length == 1) {
                parsedCodes.add(toInt(endPoints[0]));
            } else if (endPoints.length == 2) {
                int start = toInt(endPoints[0]);
                int end = toInt(endPoints[1]);
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

    }
    //TODO make sure Collector validates workdir is
    // not null on startup.
    private Path getBaseDir(HttpCollector collector) {
        if (outputDir == null) {
            return collector.getWorkDir();
        }
        return outputDir;
    }
    private CSVPrinter createCSVPrinter(Path dir, String id, String suffix) {
        String prefix = StringUtils.defaultString(fileNamePrefix);
        String safeSuffix = "";
        if (StringUtils.isNotBlank(suffix)) {
            safeSuffix = "-" + suffix;
        }
        Path file = dir.resolve(
                prefix + FileUtil.toSafeFileName(id) + safeSuffix + ".csv");
        try {
            Files.createDirectories(dir);
            CSVPrinter csv = new CSVPrinter(Files.newBufferedWriter(file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE),
                    CSVFormat.EXCEL);
            csv.printRecord("Referrer", "URL", "Status", "Reason");
            return csv;
        } catch (IOException e) {
            throw new CollectorException(
                    "Cannot create output directory for file: " + file, e);
        }
    }

    private int toInt(String num) {
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The statusCodes attribute "
                    + "can only contain valid numbers. This number is invalid: "
                    + num);
        }
    }

    @Override
    public void loadFromXML(XML xml) {
        setStatusCodes(xml.getString("statusCodes", statusCodes));
        setOutputDir(xml.getPath("outputDir", outputDir));
        setFileNamePrefix(xml.getString("fileNamePrefix", fileNamePrefix));
        setCrawlerIds(xml.getStringList("crawlerIds/id", crawlerIds));
        setCombined(xml.getBoolean("combined", combined));
        setTimestamped(xml.getBoolean("timestamped", timestamped));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.addElement("statusCodes", statusCodes);
        xml.addElement("outputDir", outputDir);
        xml.addElement("fileNamePrefix", fileNamePrefix);
        xml.addElementList("crawlerIds", "id", crawlerIds);
        xml.addElement("combined", combined);
        xml.addElement("timestamped", timestamped);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this,
                ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
