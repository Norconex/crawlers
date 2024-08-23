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
package com.norconex.crawler.web.event.listeners;

import java.nio.file.Path;
import java.util.List;

import com.norconex.crawler.web.doc.operations.link.impl.HtmlLinkExtractor;
import com.norconex.crawler.web.doc.operations.link.impl.TikaLinkExtractor;

import lombok.Data;
import lombok.experimental.Accessors;

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
@Data
@Accessors(chain = true)
@SuppressWarnings("javadoc")
public class UrlStatusCrawlerEventListenerConfig {

    public static final String DEFAULT_FILENAME_PREFIX = "urlstatuses-";

    /**
     * The coma-separated list of status codes to listen to.
     * Default is <code>null</code> (listens for all status codes).
     * See class documentation for how to specify code ranges.
     * @param statusCode HTTP status codes
     * @return status codes
     */
    private String statusCodes;

    /**
     * The local directory where this listener report will be written.
     * Default uses the collector working directory.
     * @param outputDir directory path
     * @return directory path
     */
    private Path outputDir;

    /**
     * The generated report file name prefix. See class documentation
     * for default prefix.
     * @param fileNamePrefix file name prefix
     * @return file name prefix
     */
    private String fileNamePrefix = DEFAULT_FILENAME_PREFIX;

    /**
     * Whether to add a timestamp to the file name, to ensure
     * a new one is created with each run.
     * @param timestamped <code>true</code> if timestamped
     * @return <code>true</code> if timestamped
     */
    private boolean timestamped;
}
