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

import java.io.BufferedWriter;
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorEvent;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpFetchResponseBuilder;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.url.impl.GenericLinkExtractor;
import com.norconex.collector.http.url.impl.TikaLinkExtractor;
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
 *   urlstatuses-[timestamp].tsv
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
 * {@link GenericLinkExtractor} properly extracts this information.  Same with
 * {@link TikaLinkExtractor}.  This is only a consideration when
 * using a custom link extractor.
 * </p>
 *
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;listener
 *      class="com.norconex.collector.http.crawler.event.impl.URLStatusCrawlerEventListener"&gt;
 *      &lt;statusCodes&gt;(CSV list of status codes)&lt;/statusCodes&gt;
 *      &lt;crawlerIds&gt;
 *          &lt;id&gt;(existing crawler ID)&lt;/id&gt;
 *          &lt;!-- repeat as needed --&gt;
 *      &lt;/crawlerIds&gt;
 *      &lt;outputDir&gt;(path to a directory of your choice)&lt;/outputDir&gt;
 *      &lt;fileNamePrefix&gt;(report file name prefix)&lt;/fileNamePrefix&gt;
 *      &lt;combined&gt;[false|true]&lt;/combined&gt;
 *  &lt;/listener&gt;
 * </pre>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following example will generate a broken links report by recording
 * 404 status codes (from HTTP response).
 * </p>
 * <pre>
 *  &lt;listener
 *      class="com.norconex.collector.http.crawler.event.impl.URLStatusCrawlerEventListener"&gt;
 *      &lt;statusCodes&gt;404&lt;/statusCodes&gt;
 *      &lt;outputDir&gt;/report/path/&lt;/outputDir&gt;
 *      &lt;fileNamePrefix&gt;brokenLinks&lt;/fileNamePrefix&gt;
 *  &lt;/listener&gt;
 * </pre>
 *
 * @author Pascal Essiembre
 * @since 2.2.0
 */
public class URLStatusCrawlerEventListener
        implements IEventListener<Event<?>>, IXMLConfigurable {

    public static final String DEFAULT_FILENAME_PREFIX = "urlstatuses-";

    private static final String[] NO_REFLECT_FIELDS = new String[] {
            "parsedCodes", "outputFiles"
    };

    private String statusCodes;
    private Path outputDir;
    private String fileNamePrefix;
    private final List<String> crawlerIds = new ArrayList<>();
    private boolean combined;

    // variables set when crawler starts/resumes
    private final List<Integer> parsedCodes = new ArrayList<>();
    private final Map<String, Path> outputFiles = new HashMap<>();


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
        if (event.is(HttpCollectorEvent.COLLECTOR_RUN_BEGIN)) {
            init(((HttpCollectorEvent) event).getSource());
            return;
        }

        if (!(event instanceof HttpCrawlerEvent)) {
            return;
        }

        HttpCrawlerEvent e = ((HttpCrawlerEvent) event);
        if (e.getSubject() instanceof HttpFetchResponseBuilder) {
            IHttpFetchResponse response = (IHttpFetchResponse) e.getSubject();
            if (parsedCodes.isEmpty()
                    || parsedCodes.contains(response.getStatusCode())) {
                Path outFile = outputFiles.get(
                        combined ? null : e.getSource().getId());
                if (outFile != null) {
                    HttpDocInfo crawlRef =
                            (HttpDocInfo) e.getCrawlReference();
                    writeLine(outFile, crawlRef.getReferrerReference(),
                            crawlRef.getReference(),
                            Integer.toString(response.getStatusCode()),
                            response.getReasonPhrase(),
                            true);
                }
            }
        }
    }

    private void init(HttpCollector collector) {

        Path baseDir = getBaseDir(collector);
        String timestamp =
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS).toString();

        // if combined == true, get using null to hashmap.
        if (combined) {
            outputFiles.put(null,
                    createOutFile(baseDir, collector.getId(), timestamp));
        } else {
            for (Crawler crawler : collector.getCrawlers()) {
                String id = crawler.getId();
                if (crawlerIds.contains(id)) {
                    outputFiles.put(id, createOutFile(baseDir, id, timestamp));
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
            return collector.getCollectorConfig().getWorkDir();
        }
        return outputDir;
    }
    private Path createOutFile(Path dir, String id, String suffix) {
        String prefix = StringUtils.defaultString(fileNamePrefix);
        Path file = dir.resolve(
                prefix + FileUtil.toSafeFileName(id) + "-" + suffix + ".tsv");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new CollectorException(
                    "Cannot create output directory for file: " + file, e);
        }
        writeLine(file, "Referrer", "URL", "Status", "Reason", false);
        return file;
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

    private void writeLine(
            Path file,
            String referrer, String url, String status,
            String cause, boolean append) {

        try (BufferedWriter out = Files.newBufferedWriter(file, append
                ? StandardOpenOption.APPEND
                : StandardOpenOption.CREATE,
                  StandardOpenOption.TRUNCATE_EXISTING,
                  StandardOpenOption.WRITE)) {
            out.write(StringUtils.trimToEmpty(referrer));
            out.write('\t');
            out.write(StringUtils.trimToEmpty(url));
            out.write('\t');
            out.write(StringUtils.trimToEmpty(status));
            out.write('\t');
            out.write(StringUtils.trimToEmpty(cause));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            throw new CollectorException(
                    "Cannot write link report to file: " + file, e);
        }
    }

    @Override
    public void loadFromXML(XML xml) {
        setStatusCodes(xml.getString("statusCodes", statusCodes));
        setOutputDir(xml.getPath("outputDir", outputDir));
        setFileNamePrefix(xml.getString("fileNamePrefix", fileNamePrefix));
        setCrawlerIds(xml.getStringList("crawlerIds/id", crawlerIds));
        setCombined(xml.getBoolean("combined", combined));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.addElement("statusCodes", statusCodes);
        xml.addElement("outputDir", outputDir);
        xml.addElement("fileNamePrefix", fileNamePrefix);
        xml.addElementList("crawlerIds", "id", crawlerIds);
        xml.addElement("combined", combined);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other, NO_REFLECT_FIELDS);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, NO_REFLECT_FIELDS);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this,
                ToStringStyle.SHORT_PREFIX_STYLE).setExcludeFieldNames(
                        NO_REFLECT_FIELDS).toString();
    }
}
