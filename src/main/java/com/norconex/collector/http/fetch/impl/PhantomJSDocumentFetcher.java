/* Copyright 2017-2021 Norconex Inc.
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
package com.norconex.collector.http.fetch.impl;

import static com.norconex.collector.http.fetch.HttpMethod.GET;
import static java.util.Optional.ofNullable;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.doc.CrawlDocMetadata;
import com.norconex.collector.http.doc.HttpCrawlState;
import com.norconex.collector.http.fetch.AbstractHttpFetcher;
import com.norconex.collector.http.fetch.HttpFetchException;
import com.norconex.collector.http.fetch.HttpFetchResponseBuilder;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.fetch.impl.webdriver.WebDriverHttpFetcher;
import com.norconex.collector.http.processor.impl.ScaledImage;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.exec.ExecUtil;
import com.norconex.commons.lang.exec.SystemCommand;
import com.norconex.commons.lang.exec.SystemCommandException;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.InputStreamLineListener;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.util.CharsetUtil;

/**
 * <h2>Deprecation notice</h2>
 * <p>
 * PhantomJS headless browser is no longer maintained by its owner.
 * As such, starting with version 3.0.0, use of PhantomJSDocumentFetcher is
 * strongly discouraged and HttpClientProxy support for it has been dropped.
 * With more popular browsers (e.g. Chrome) now supporting operating
 * in headless mode, we now have more stable options.  Please consider
 * using {@link WebDriverHttpFetcher} instead when attempting to crawl
 * a JavaScript-driven website.
 * </p>
 * <hr>
 * <p>
 * An alternative to the {@link GenericHttpFetcher} which relies on an
 * external <a href="http://phantomjs.org/">PhantomJS</a> installation
 * to fetch web pages.  While less efficient, this implementation is meant
 * to provide some way to crawl sites making heavy use of JavaScript to render
 * their pages. This class tells the PhantomJS headless browser to wait a
 * certain amount of time for the page to load extra content via Ajax requests
 * before grabbing all loaded HTML.
 * </p>
 *
 * <h3>Considerations</h3>
 * <p>
 * Relying on an external software to fetch pages is slower and not as
 * scalable and may be less stable. The use of {@link GenericHttpFetcher}
 * should be preferred whenever possible. Use at your own risk.
 * Use PhantomJS 2.1 (or possibly higher).
 * </p>
 *
 * <h3>Handling of non-HTML Pages</h3>
 * <p>
 * It is usually only useful to use PhantomJS for HTML pages with JavaScript.
 * Other types of documents are fetched using an instance of
 * {@link GenericHttpFetcher}
 * To find out if we are dealing with an HTML
 * documents, this fetcher needs to know the content type first.
 * By default, the content type
 * of a document is not known before a physical copy is obtained.
 * This means PhantomJS has to first download the document and if it is not an
 * HTML document at that point, it will be re-downloaded again with the generic
 * document fetcher.
 * By default, these content-types are considered HTML:
 * </p>
 * <pre>
 * text/html, application/xhtml+xml, application/vnd.wap.xhtml+xml, application/x-asp
 * </pre>
 * <p>
 * Those can be overwritten with {@link #setContentTypePattern(String)}.
 * </p>
 * <h4>Avoid double-downloads</h4>
 * <p>
 * To avoid downloading the document twice as described above, you can
 * configure a metadata fetcher (such as {@link GenericHttpFetcher}).  This
 * will attempt get the content type by first making an HTTP HEAD request.
 * </p>
 * <p>
 * Alternatively, if you have a URL pattern that identifies your HTML pages
 * (and only HTML pages), you can specify it using
 * {@link #setReferencePattern(String)}.  Only URLs matching the provided
 * regular expression will be fetched by PhantomJS.  By default there is no
 * pattern for discriminating on URL references.
 * </p>
 *
 * <h3>Taking screenshots of pages</h3>
 * <p>
 * Thanks to PhantomJS, one can save images of pages being crawled, including
 * those rendered with JavaScript!
 * </p>
 * <p>
 * <b>Since 2.8.0</b>, you have to explicitely enabled screenshots with
 * {@link #setScreenshotEnabled(boolean)}. Also screenshots now share the same
 * size by default.
 * In addition, you can now control how screenshots are resized and how
 * they are stored stored.
 * Storage options:
 * </p>
 * <ul>
 *   <li>
 *     <b>inline</b>: Stores a Base64 string of the scaled image, in the format
 *     specified, in a <code>collector.featured-image-inline</code> field.
 *     The string is ready to be
 *     used inline, in a &lt;img src="..."&gt; tag.
 *   </li>
 *   <li>
 *     <b>disk</b>: Stores the scaled image on the file system, in the format
 *     and directory specified. A reference to the file on disk is stored
 *     in a <code>collector.featured-image-path</code> field.
 *   </li>
 * </ul>
 *
 * <p>
 * <b>Since 2.8.0</b>, it is possible to specify a resource timeout so that
 * slow individual page resources do not cause PhantomJS to hang for a
 * long time.
 * </p>
 *
 * <h3>PhantomJS exit values</h3>
 * <p>
 * <b>Since 2.9.1</b>, it is possible to specify which PhantomJS exit values
 * are to be considered "valid". Use a comma-separated-list of integers using
 * the {@link #setValidExitCodes(int...)} method. By default, only zero is
 * considered valid.
 * </p>
 *
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * <h3>XML configuration usage:</h3>
 *
 * <pre>
 *  &lt;documentFetcher
 *      class="com.norconex.collector.http.fetch.impl.PhantomJSDocumentFetcher"
 *      detectContentType="[false|true]" detectCharset="[false|true]"
 *      screenshotEnabled="[false|true]"&gt;
 *      &lt;exePath&gt;(path to PhantomJS executable)&lt;/exePath&gt;
 *      &lt;scriptPath&gt;
 *          (Optional path to a PhantomJS script. Defaults to scripts/phantom.js)
 *      &lt;/scriptPath&gt;
 *      &lt;renderWaitTime&gt;
 *          (Milliseconds to wait for the entire page to load.
 *           Defaults to 3000, i.e., 3 seconds.)
 *      &lt;/renderWaitTime&gt;
 *      &lt;resourceTimeout&gt;
 *          (Optional Milliseconds to wait for a page resource to load.
 *           Defaults is unspecified.)
 *      &lt;/resourceTimeout&gt;
 *      &lt;options&gt;
 *        &lt;opt&gt;(optional extra PhantomJS command-line option)&lt;/opt&gt;
 *        &lt;!-- You can have multiple opt tags --&gt;
 *      &lt;/options&gt;
 *      &lt;referencePattern&gt;
 *          (Regular expression matching URLs for which to use the
 *           PhantomJS browser. Non-matching URLs will fallback
 *           to using GenericDocumentFetcher.)
 *      &lt;/referencePattern&gt;
 *      &lt;contentTypePattern&gt;
 *          (Regular expression matching content types for which to use
 *           the PhantomJS browser. Non-matching content types will use
 *           the GenericDocumentFetcher.)
 *      &lt;/contentTypePattern&gt;
 *      &lt;validExitCodes&gt;(defaults to 0)&lt;/validExitCodes&gt;
 *      &lt;validStatusCodes&gt;(defaults to 200)&lt;/validStatusCodes&gt;
 *      &lt;notFoundStatusCodes&gt;(defaults to 404)&lt;/notFoundStatusCodes&gt;
 *      &lt;headersPrefix&gt;(string to prefix headers)&lt;/headersPrefix&gt;
 *
 *      &lt;!-- Only applicable when screenshotEnabled is true: --&gt;
 *      &lt;screenshotDimensions&gt;
 *          (Pixel size of the browser page area to capture: [width]x[height].
 *           E.g., 800x600.  Only used when a screenshot path is specified.
 *           Default is undefined. It will try to load all it can and may
 *           produce vertically long images.)
 *      &lt;/screenshotDimensions&gt;
 *      &lt;screenshotZoomFactor&gt;
 *          (A decimal value to scale the screenshot image.
 *           E.g., 0.25  will make the image 25% its regular size,
 *           which is 25% of the above dimension if specified.
 *           Default is 1, i.e., 100%)
 *      &lt;/screenshotZoomFactor&gt;
 *      &lt;screenshotScaleDimensions&gt;
 *         (Target pixel size the main image should be scaled to.
 *          Default is 300.)
 *      &lt;/screenshotScaleDimensions&gt;
 *      &lt;screenshotScaleStretch&gt;
 *         [false|true]
 *         (Whether to stretch to match scale size. Default keeps aspect ratio.)
 *      &lt;/screenshotScaleStretch&gt;
 *      &lt;screenshotScaleQuality&gt;
 *          [auto|low|medium|high|max]
 *          (Default is "auto", which tries the best balance between quality
 *           and speed based on image size. The lower the quality the faster
 *           it is to scale images.)
 *      &lt;/screenshotScaleQuality&gt;
 *      &lt;screenshotImageFormat&gt;
 *         (Target format of stored image. E.g., "jpg", "png", "gif", "bmp", ...
 *          Default is "png")
 *      &lt;/screenshotImageFormat&gt;
 *      &lt;screenshotStorage&gt;
 *         [disk|inline]
 *         (One or both, comma-separated. Default is "disk".)
 *      &lt;/screenshotStorage&gt;
 *
 *      &lt;!-- Only applicable for "disk" storage: --&gt;
 *      &lt;screenshotStorageDiskDir structure="[url2path|date|datetime]"&gt;
 *          (Path where to save screenshots.)
 *      &lt;/screenshotStorageDiskDir&gt;
 *      &lt;screenshotStorageDiskField&gt;
 *          (Overwrite default field where to store the screenshot path.)
 *      &lt;/screenshotStorageDiskField&gt;
 *
 *      &lt;!-- Only applicable for "inline" storage: --&gt;
 *      &lt;screenshotStorageInlineField&gt;
 *          (Overwrite default field where to store the inline screenshot.)
 *      &lt;/screenshotStorageInlineField&gt;
 *
 *  &lt;/documentFetcher&gt;
 * </pre>
 * <p>
 * When specifying an image size, the format is <code>[width]x[height]</code>
 * or a single value. When a single value is used, that value represents both
 * the width and height (i.e., a square).
 * </p>
 * <p>
 * The "validStatusCodes" and "notFoundStatusCodes" elements expect a
 * coma-separated list of HTTP response code.  If a code is added in both
 * elements, the valid list takes precedence.
 * </p>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following configures HTTP Collector to use PhantomJS with a
 * proxy to use HttpClient, only for URLs ending with ".html".
 * </p>
 * <pre>
 *  &lt;httpcollector id="MyHttpCollector"&gt;
 *    ...
 *    &lt;crawlers&gt;
 *      &lt;crawler id="MyCrawler"&gt;
 *        ...
 *        &lt;documentFetcher class="com.norconex.collector.http.fetch.impl.PhantomJSDocumentFetcher"&gt;
 *          &lt;exePath&gt;/path/to/phantomjs.exe&lt;/exePath&gt;
 *          &lt;renderWaitTime&gt;5000&lt;/renderWaitTime&gt;
 *          &lt;referencePattern&gt;^.*\.html$&lt;/referencePattern&gt;
 *        &lt;/documentFetcher&gt;
 *        ...
 *      &lt;/crawler&gt;
 *    &lt;/crawlers&gt;
 *    ...
 *    &lt;!-- Only if you need to use the HttpClient proxy (see documentation): --&gt;
 *    &lt;collectorListeners&gt;
 *      &lt;listener class="com.norconex.collector.http.fetch.impl.HttpClientProxyCollectorListener" /&gt;
 *    &lt;/collectorListeners&gt;
 *  &lt;/httpcollector&gt;
 * </pre>
 *
 *
 * @author Pascal Essiembre
 * @since 2.7.0
 * @deprecated Since 3.0.0 use {@link WebDriverHttpFetcher}
 */
@Deprecated
public class PhantomJSDocumentFetcher extends AbstractHttpFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(
			PhantomJSDocumentFetcher.class);

    public enum Storage { INLINE, DISK }
    public enum StorageDiskStructure { URL2PATH, DATE, DATETIME }
    public enum Quality {
        AUTO(Method.AUTOMATIC),
        LOW(Method.SPEED),
        MEDIUM(Method.BALANCED),
        HIGH(Method.QUALITY),
        MAX(Method.ULTRA_QUALITY);
        private Method scalrMethod;
        Quality(Method scalrMethod) {
            this.scalrMethod = scalrMethod;
        }
    }

    public static final String DEFAULT_SCRIPT_PATH = "scripts/phantom.js";
    public static final int DEFAULT_RENDER_WAIT_TIME = 3000;
    public static final float DEFAULT_SCREENSHOT_ZOOM_FACTOR = 1.0f;
    public static final String DEFAULT_CONTENT_TYPE_PATTERN =
            "^(text/html|application/xhtml\\+xml|vnd.wap.xhtml\\+xml|x-asp)$";
    public static final String DEFAULT_SCREENSHOT_STORAGE_DISK_DIR =
            "./screenshots";
    public static final Storage DEFAULT_SCREENSHOT_STORAGE = Storage.DISK;
    public static final String DEFAULT_SCREENSHOT_IMAGE_FORMAT = "png";

    public static final Dimension DEFAULT_SCREENSHOT_SCALE_SIZE =
            new Dimension(300, 300);

    static final List<Integer> DEFAULT_VALID_STATUS_CODES =
            Collections.unmodifiableList(Arrays.asList(HttpStatus.SC_OK));
    static final List<Integer> DEFAULT_NOT_FOUND_STATUS_CODES =
            Collections.unmodifiableList(
                    Arrays.asList(HttpStatus.SC_NOT_FOUND));

    public static final String COLLECTOR_PHANTOMJS_SCREENSHOT_PATH =
            CrawlDocMetadata.PREFIX + "phantomjs-screenshot-path";
    public static final String COLLECTOR_PHANTOMJS_SCREENSHOT_INLINE =
            CrawlDocMetadata.PREFIX + "phantomjs-screenshot-inline";


    private String exePath;
    private String scriptPath = DEFAULT_SCRIPT_PATH;
    private int renderWaitTime = DEFAULT_RENDER_WAIT_TIME;
    private int resourceTimeout = -1;

    private final List<String> options = new ArrayList<>();
    private final List<Integer> validExitCodes =
            new ArrayList<>(Arrays.asList(0));

    private String contentTypePattern = DEFAULT_CONTENT_TYPE_PATTERN;
    private String referencePattern;

    private final GenericHttpFetcherConfig fetcherConfig =
            new GenericHttpFetcherConfig();

    private boolean screenshotEnabled;
    private String screenshotStorageDiskDir =
            DEFAULT_SCREENSHOT_STORAGE_DISK_DIR;
    private String screenshotStorageDiskField =
            COLLECTOR_PHANTOMJS_SCREENSHOT_PATH;
    private String screenshotStorageInlineField =
            COLLECTOR_PHANTOMJS_SCREENSHOT_INLINE;
    private Dimension screenshotDimensions;
    private float screenshotZoomFactor = DEFAULT_SCREENSHOT_ZOOM_FACTOR;
    private Dimension screenshotScaleDimensions = DEFAULT_SCREENSHOT_SCALE_SIZE;
    private boolean screenshotScaleStretch;
    private String screenshotImageFormat = DEFAULT_SCREENSHOT_IMAGE_FORMAT;
    private final List<Storage> screenshotStorage =
            new ArrayList<>(Arrays.asList(DEFAULT_SCREENSHOT_STORAGE));
    private StorageDiskStructure screenshotStorageDiskStructure =
            StorageDiskStructure.DATETIME;
    private Quality screenshotScaleQuality = Quality.AUTO;

    private boolean initialized;

    public PhantomJSDocumentFetcher() {
        super();
    }
    public PhantomJSDocumentFetcher(int[] validStatusCodes) {
        super();
        setValidStatusCodes(validStatusCodes);
    }

    public String getExePath() {
        return exePath;
    }
    public void setExePath(String exePath) {
        this.exePath = exePath;
    }
    public String getScriptPath() {
        return scriptPath;
    }
    public void setScriptPath(String scriptPath) {
        this.scriptPath = scriptPath;
    }
    public int getRenderWaitTime() {
        return renderWaitTime;
    }
    public void setRenderWaitTime(int renderWaitTime) {
        this.renderWaitTime = renderWaitTime;
    }

    public List<String> getOptions() {
        return Collections.unmodifiableList(options);
    }
    /**
     * Sets optional extra PhantomJS command-line options.
     * @param options extra command line arguments
     * @since 3.0.0
     */
    public void setOptions(List<String> options) {
        CollectionUtil.setAll(this.options, options);
    }
    /**
     * Sets optional extra PhantomJS command-line options.
     * @param options extra command line arguments
     */
    public void setOptions(String... options) {
        CollectionUtil.setAll(this.options, options);
    }

    /**
     * Gets the directory where screenshots are saved when storage is "disk".
     * Default is {@value #DEFAULT_SCREENSHOT_STORAGE_DISK_DIR}.
     * @return directory
     * @since 2.8.0
     */
    public String getScreenshotStorageDiskDir() {
        return screenshotStorageDiskDir;
    }
    /**
     * Sets the directory where screenshots are saved when storage is "disk".
     * Use this method to overwrite the default
     * ({@value #DEFAULT_SCREENSHOT_STORAGE_DISK_DIR}).
     * @param screenshotStorageDiskDir directory
     * @since 2.8.0
     */
    public void setScreenshotStorageDiskDir(String screenshotStorageDiskDir) {
        this.screenshotStorageDiskDir = screenshotStorageDiskDir;
    }

    /**
     * Gets the target document metadata field where to store the path
     * to thescreen shot image file when storage is "disk".
     * Default is {@value #COLLECTOR_PHANTOMJS_SCREENSHOT_PATH}.
     * @return field name
     * @since 2.8.0
     */
    public String getScreenshotStorageDiskField() {
        return screenshotStorageDiskField;
    }
    /**
     * Sets the target document metadata field where to store the path
     * to thescreen shot image file when storage is "disk".
     * Use this method to overwrite the default
     * ({@value #COLLECTOR_PHANTOMJS_SCREENSHOT_PATH}).
     * @param screenshotStorageDiskField field name
     * @since 2.8.0
     */
    public void setScreenshotStorageDiskField(
            String screenshotStorageDiskField) {
        this.screenshotStorageDiskField = screenshotStorageDiskField;
    }

    /**
     * Gets the target document metadata field where to store the inline
     * (Base64) screenshot image when storage is "inline".
     * Default is {@value #COLLECTOR_PHANTOMJS_SCREENSHOT_INLINE}.
     * @return field name
     * @since 2.8.0
     */
    public String getScreenshotStorageInlineField() {
        return screenshotStorageInlineField;
    }
    /**
     * Sets the target document metadata field where to store the inline
     * (Base64) screenshot image when storage is "inline".
     * Use this method to overwrite the default
     * ({@value #COLLECTOR_PHANTOMJS_SCREENSHOT_INLINE}).
     * @param screenshotStorageInlineField field name
     * @since 2.8.0
     */
    public void setScreenshotStorageInlineField(
            String screenshotStorageInlineField) {
        this.screenshotStorageInlineField = screenshotStorageInlineField;
    }

    /**
     * Gets whether to enable taking screenshot of crawled web pages.
     * @return <code>true</code> if enabled
     * @since 2.8.0
     */
    public boolean isScreenshotEnabled() {
        return screenshotEnabled;
    }
    /**
     * Sets whether to enable taking screenshot of crawled web pages.
     * @param screenshotEnabled <code>true</code> if enabled
     * @since 2.8.0
     */
    public void setScreenshotEnabled(boolean screenshotEnabled) {
        this.screenshotEnabled = screenshotEnabled;
    }

    public Dimension getScreenshotDimensions() {
        return screenshotDimensions;
    }
    public void setScreenshotDimensions(int width, int height) {
        this.screenshotDimensions = new Dimension(width, height);
    }
    public void setScreenshotDimensions(Dimension screenshotDimensions) {
        this.screenshotDimensions = screenshotDimensions;
    }

    public float getScreenshotZoomFactor() {
        return screenshotZoomFactor;
    }
    public void setScreenshotZoomFactor(float screenshotZoomFactor) {
        this.screenshotZoomFactor = screenshotZoomFactor;
    }

    /**
     * Sets valid PhantomJS exit values (defaults to 0).
     * @return valid exit codes
     * @since 2.9.1
     */
    public List<Integer> getValidExitCodes() {
        return Collections.unmodifiableList(validExitCodes);
    }
    /**
     * Sets valid PhantomJS exit values (defaults to 0).
     * @param validExitCodes valid exit codes
     * @since 2.9.1
     */
    public void setValidExitCodes(List<Integer> validExitCodes) {
        CollectionUtil.setAll(this.validExitCodes, validExitCodes);
    }
    /**
     * Sets valid PhantomJS exit values (defaults to 0).
     * @param validExitCodes valid exit codes
     * @since 2.9.1
     */
    public void setValidExitCodes(int... validExitCodes) {
        CollectionUtil.setAll(this.validExitCodes,
                ArrayUtils.toObject(validExitCodes));
    }

    public List<Integer> getValidStatusCodes() {
        return fetcherConfig.getValidStatusCodes();
    }
    /**
     * Gets valid HTTP response status codes.
     * @param validStatusCodes valid status codes
     * @since 3.0.0
     */
    public void setValidStatusCodes(List<Integer> validStatusCodes) {
        fetcherConfig.setValidStatusCodes(validStatusCodes);
    }
    /**
     * Gets valid HTTP response status codes.
     * @param validStatusCodes valid status codes
     */
    public void setValidStatusCodes(int... validStatusCodes) {
        fetcherConfig.setValidStatusCodes(validStatusCodes);
    }

    /**
     * Gets HTTP status codes to be considered as "Not found" state.
     * Default is 404.
     * @return "Not found" codes
     */
    public List<Integer> getNotFoundStatusCodes() {
        return fetcherConfig.getNotFoundStatusCodes();
    }
    /**
     * Sets HTTP status codes to be considered as "Not found" state.
     * @param notFoundStatusCodes "Not found" codes
     */
    public final void setNotFoundStatusCodes(int... notFoundStatusCodes) {
        fetcherConfig.setNotFoundStatusCodes(notFoundStatusCodes);
    }
    /**
     * Sets HTTP status codes to be considered as "Not found" state.
     * @param notFoundStatusCodes "Not found" codes
     * @since 3.0.0
     */
    public final void setNotFoundStatusCodes(
            List<Integer> notFoundStatusCodes) {
        fetcherConfig.setNotFoundStatusCodes(notFoundStatusCodes);
    }

    public String getHeadersPrefix() {
        return fetcherConfig.getHeadersPrefix();
    }
    public void setHeadersPrefix(String headersPrefix) {
        fetcherConfig.setHeadersPrefix(headersPrefix);
    }
    public boolean isDetectContentType() {
        return fetcherConfig.isForceContentTypeDetection();
    }
    public void setDetectContentType(boolean detectContentType) {
        fetcherConfig.setForceContentTypeDetection(detectContentType);
    }
    public boolean isDetectCharset() {
        return fetcherConfig.isForceCharsetDetection();
    }
    public void setDetectCharset(boolean detectCharset) {
        fetcherConfig.setForceCharsetDetection(detectCharset);
    }
    public String getContentTypePattern() {
        return contentTypePattern;
    }
    public void setContentTypePattern(String contentTypePattern) {
        this.contentTypePattern = contentTypePattern;
    }
    public String getReferencePattern() {
        return referencePattern;
    }
    public void setReferencePattern(String referencePattern) {
        this.referencePattern = referencePattern;
    }

    /**
     * Gets the milliseconds timeout after which any resource requested will
     * stop trying and proceed with other parts of the page.
     * @return the timeout value, or <code>-1</code> if undefined
     * @since 2.8.0
     */
    public int getResourceTimeout() {
        return resourceTimeout;
    }
    /**
     * Sets the milliseconds timeout after which any resource requested will
     * stop trying and proceed with other parts of the page.
     * @param resourceTimeout the timeout value, or <code>-1</code>
     *                        for undefined
     * @since 2.8.0
     */
    public void setResourceTimeout(int resourceTimeout) {
        this.resourceTimeout = resourceTimeout;
    }
    /**
     * Gets the pixel dimensions we want the stored screenshot to have.
     * @return dimension
     * @since 2.8.0
     */
    public Dimension getScreenshotScaleDimensions() {
        return screenshotScaleDimensions;
    }
    /**
     * Sets the pixel dimensions we want the stored screenshot to have.
     * @param screenshotScaleDimensions dimension
     * @since 2.8.0
     */
    public void setScreenshotScaleDimensions(
            Dimension screenshotScaleDimensions) {
        this.screenshotScaleDimensions = screenshotScaleDimensions;
    }
    /**
     * Sets the pixel dimensions we want the stored screenshot to have.
     * @param width image width
     * @param height image height
     * @since 2.8.0
     */
    public void setScreenshotScaleDimensions(int width, int height) {
        this.screenshotScaleDimensions = new Dimension(width, height);
    }

    /**
     * Gets whether the screenshot should be stretch to to fill all
     * the scale dimensions.  Default keeps aspect ratio.
     * @return <code>true</code> to stretch
     * @since 2.8.0
     */
    public boolean isScreenshotScaleStretch() {
        return screenshotScaleStretch;
    }
    /**
     * Sets whether the screenshot should be stretch to to fill all
     * the scale dimensions.  Default keeps aspect ratio.
     * @param screenshotScaleStretch <code>true</code> to stretch
     * @since 2.8.0
     */
    public void setScreenshotScaleStretch(boolean screenshotScaleStretch) {
        this.screenshotScaleStretch = screenshotScaleStretch;
    }
    /**
     * Gets the screenshot image format (jpg, png, gif, bmp, etc.).
     * @return image format
     * @since 2.8.0
     */
    public String getScreenshotImageFormat() {
        return screenshotImageFormat;
    }
    /**
     * Sets the screenshot image format (jpg, png, gif, bmp, etc.).
     * @param screenshotImageFormat image format
     * @since 2.8.0
     */
    public void setScreenshotImageFormat(String screenshotImageFormat) {
        this.screenshotImageFormat = screenshotImageFormat;
    }
    /**
     * Gets the screenshot storage mechanisms.
     * @return storage mechanisms (never <code>null</code>)
     * @since 2.8.0
     */
    public List<Storage> getScreenshotStorage() {
        return Collections.unmodifiableList(screenshotStorage);
    }
    /**
     * Sets the screenshot storage mechanisms.
     * @param screenshotStorage storage mechanisms
     * @since 3.0.0
     */
    public void setScreenshotStorage(List<Storage> screenshotStorage) {
        CollectionUtil.setAll(this.screenshotStorage, screenshotStorage);
    }
    /**
     * Sets the screenshot storage mechanisms.
     * @param screenshotStorage storage mechanisms
     * @since 2.8.0
     */
    public void setScreenshotStorage(Storage... screenshotStorage) {
        CollectionUtil.setAll(this.screenshotStorage, screenshotStorage);
    }

    /**
     * Gets the screenshot directory structure to create when storage
     * is "disk".
     * @return directory structure
     * @since 2.8.0
     */
    public StorageDiskStructure getScreenshotStorageDiskStructure() {
        return screenshotStorageDiskStructure;
    }
    /**
     * Sets the screenshot directory structure to create when storage
     * is "disk".
     * @param screenshotStorageDiskStructure directory structure
     * @since 2.8.0
     */
    public void setScreenshotStorageDiskStructure(
            StorageDiskStructure screenshotStorageDiskStructure) {
        this.screenshotStorageDiskStructure = screenshotStorageDiskStructure;
    }
    /**
     * Gets the screenshot scaling quality to use when when storage
     * is "disk" or "inline".
     * Default is {@link Quality#AUTO}
     * @return quality
     * @since 2.8.0
     */
    public Quality getScreenshotScaleQuality() {
        return screenshotScaleQuality;
    }
    /**
     * Sets the screenshot scaling quality to use when when storage
     * is "disk" or "inline".
     * @param screenshotScaleQuality quality
     * @since 2.8.0
     */
    public void setScreenshotScaleQuality(Quality screenshotScaleQuality) {
        this.screenshotScaleQuality = screenshotScaleQuality;
    }


    @Override
    public String getUserAgent() {
        // could not set
        return null;
    }

    @Override
    public boolean accept(Doc doc, HttpMethod httpMethod) {
        if (!accept(httpMethod)) {
            return false;
        }

        // If there is a reference pattern and it does not match, reject
        if (StringUtils.isNotBlank(referencePattern)
                && !isHTMLByReference(doc.getReference())) {
            LOG.debug("Reference pattern does not match for URL: {}",
                    doc.getReference());
            return false;
        }
        // If content type is known and ct pattern does not match, use generic
        String contentType = getContentType(doc);
        if (StringUtils.isNotBlank(contentTypePattern)
                && StringUtils.isNotBlank(contentType)
                && !isHTMLByContentType(contentType)) {
            LOG.debug("Content type ({}) known before fetching and does not "
                    + "match pattern for URL: {}",
                    contentType, doc.getReference());
            return false;
        }
        return true;
    }
    @Override
    protected boolean accept(HttpMethod httpMethod) {
        return HttpMethod.GET.is(httpMethod);
    }

    @Override
    public IHttpFetchResponse fetch(CrawlDoc doc, HttpMethod httpMethod)
            throws HttpFetchException {

        HttpMethod method = ofNullable(httpMethod).orElse(GET);
        if (method != GET) {
            return HttpFetchResponseBuilder.unsupported().setReasonPhrase(
                    "HTTP " + httpMethod + " method not supported.").create();
        }

        init();
        validate();

        // Fetch using PhantomJS
        try {
            return fetchPhantomJSDocument(doc);
        } catch (SystemCommandException | IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.error("Cannot fetch document: " + doc.getReference()
                        + " (" + e.getMessage() + ")", e);
            } else {
                LOG.error("Cannot fetch document: " + doc.getReference()
                        + " (" + e.getMessage() + ")");
            }
            throw new CollectorException(e);
        }
    }

    private synchronized void init() {
        if (initialized) {
            return;
        }
        LOG.info("PhantomJS screenshot enabled: {}", screenshotEnabled);
        initialized = true;
    }

    private IHttpFetchResponse fetchPhantomJSDocument(Doc doc)
            throws IOException, SystemCommandException {

        PhantomJSArguments p = new PhantomJSArguments(this, doc);
	    SystemCommand cmd = createPhantomJSCommand(p);

	    CmdOutputGrabber output = new CmdOutputGrabber(
	            cmd, doc.getMetadata(), getHeadersPrefix());
	    cmd.addErrorListener(output);
	    cmd.addOutputListener(output);

	    int exit = cmd.execute();
        boolean exitValid = (validExitCodes.isEmpty() && exit == 0)
                || validExitCodes.contains(exit);

        int statusCode = output.getStatusCode();
        String reason = output.getStatusText();

        HttpFetchResponseBuilder responseBuilder =
                new HttpFetchResponseBuilder()
                        .setStatusCode(statusCode)
                        .setReasonPhrase(reason)
                        .setUserAgent(getUserAgent());


        // set Content-Type HTTP metadata obtained from CONTENTTYPE output
        // if not obtained via regular headers
        if (!doc.getMetadata().containsKey(HttpHeaders.CONTENT_TYPE)
                && StringUtils.isNotBlank(output.getContentType())) {
            doc.getMetadata().set(
                    HttpHeaders.CONTENT_TYPE, output.getContentType());
        }

        if (StringUtils.isNotBlank(output.getError())) {
            LOG.error("PhantomJS:{}", output.getError());
        }
        if (StringUtils.isNotBlank(output.getInfo())) {
            LOG.info("PhantomJS:{}", output.getInfo());
        }
        if (StringUtils.isNotBlank(output.getDebug())) {
            LOG.debug("PhantomJS:{}", output.getDebug());
        }

        if (StringUtils.isNotBlank(output.getRedirect())) {
            responseBuilder.setRedirectTarget(output.getRedirect());
        }

        // deal with screenshot regardless whether execution failed or not
        handleScreenshot(p, doc);

        // VALID response
        if (exitValid
                && fetcherConfig.getValidStatusCodes().contains(statusCode)) {
            //--- Fetch body
            for (int i = 0; i < 100; i++) {
                // it has been observed that sometimes the file does not yet
                // exists because it is not done being written to when
                // PhandomJS returns.  In case this is what's happening here
                // we wait up to 10 seconds for the file to be created.
                if (p.outFile.toFile().exists()) {
                    break;
                }
                Sleeper.sleepMillis(100);
            }
            doc.setContent(doc.getContent().newInputStream(p.outFile));
            //read a copy to force caching
            IOUtils.copy(doc.getContent(), new NullOutputStream());

            performDetection(doc);

            String contentType = getContentType(doc);
            if (!isHTMLByContentType(contentType)) {
                LOG.debug("Not a matching content type ({}) "
                    + "after download, re-downloading with "
                    + "GenericDocumentFetcher for: {}",
                    contentType, doc.getReference());
                return null;
            }
            return responseBuilder.setCrawlState(HttpCrawlState.NEW).create();
        }

        // INVALID response
        if (LOG.isTraceEnabled()) {
            LOG.trace("Rejected response content: {}",
                    FileUtils.readFileToString(
                            p.outFile.toFile(), StandardCharsets.UTF_8));
        }
        if (fetcherConfig.getNotFoundStatusCodes().contains(statusCode)) {
            return responseBuilder.setCrawlState(
                    HttpCrawlState.NOT_FOUND).create();
        }
        if (!exitValid) {
            return responseBuilder
                    .setCrawlState(HttpCrawlState.BAD_STATUS)
                    .setStatusCode(exit)
                    .setReasonPhrase(
                            "PhantomJS execution failed with exit code " + exit)
                    .create();
        }
        LOG.debug("Unsupported HTTP Response: {}", reason);
        return responseBuilder.setCrawlState(
                HttpCrawlState.BAD_STATUS).create();
	}

    private void handleScreenshot(PhantomJSArguments p, Doc doc) {

        // must be enabled
        if (!isScreenshotEnabled()) {
            return;
        }

        if (!p.phantomScreenshotFile.toFile().isFile()) {
            LOG.error("Screenshot file not created for {}", doc.getReference());
            return;
        }

        BufferedImage bi = null;
        try {
            bi = ImageIO.read(p.phantomScreenshotFile.toFile());
        } catch (IOException e) {
            LOG.error("Could not load screenshot for: \"{}", doc.getReference()
                    + "\". It was saved here: "
                    + p.phantomScreenshotFile.toAbsolutePath(), e);
            return;
        }
        try {
            FileUtil.delete(p.phantomScreenshotFile.toFile());
        } catch (IOException e) {
            LOG.warn("Could not delete temp screenshot file: "
                    + p.phantomScreenshotFile, e);
        }
        if (bi == null) {
            LOG.debug("Image is null for: {}", doc.getReference());
            return;
        }

        Dimension dim = new Dimension(bi.getWidth(), bi.getHeight());
        bi = scale(bi);

        ScaledImage img = new ScaledImage(doc.getReference(), dim, bi);

        try {
            if (screenshotStorage.contains(Storage.INLINE)) {
                doc.getMetadata().add(
                        Objects.toString(screenshotStorageInlineField,
                                COLLECTOR_PHANTOMJS_SCREENSHOT_INLINE),
                        img.toHTMLInlineString(screenshotImageFormat));
            }
            if (screenshotStorage.contains(Storage.DISK)) {
                String phantomScreenshotDir =
                        DEFAULT_SCREENSHOT_STORAGE_DISK_DIR;
                if (StringUtils.isNotBlank(screenshotStorageDiskDir)) {
                    phantomScreenshotDir = screenshotStorageDiskDir;
                }
                File diskDir = new File(phantomScreenshotDir);
                File imageFile = null;
                if (screenshotStorageDiskStructure
                        == StorageDiskStructure.URL2PATH) {
                    imageFile = new File(FileUtil.createURLDirs(
                            diskDir, img.getUrl(), true).getAbsolutePath()
                            + "." + screenshotImageFormat);
                } else if (screenshotStorageDiskStructure
                        == StorageDiskStructure.DATE) {
                    String fileId = Long.toString(TimeIdGenerator.next());
                    imageFile = new File(FileUtil.createDateDirs(
                            diskDir), fileId + "." + screenshotImageFormat);
                } else { // DATETIME
                    String fileId = Long.toString(TimeIdGenerator.next());
                    imageFile = new File(FileUtil.createDateTimeDirs(
                            diskDir), fileId + "." + screenshotImageFormat);
                }
                ImageIO.write(img.getImage(), screenshotImageFormat, imageFile);
                doc.getMetadata().add(
                        Objects.toString(screenshotStorageDiskField,
                                COLLECTOR_PHANTOMJS_SCREENSHOT_PATH),
                        imageFile.getCanonicalPath());
            }
        } catch (IOException e) {
            LOG.error("Could not store screenshot for "
                    + doc.getReference(), e);
        }
    }

    //TODO share with FeaturedImageProcessor
    private BufferedImage scale(BufferedImage origImg) {

        // If scale is null, return as is (no scaling).
        if (screenshotScaleDimensions == null) {
            return origImg;
        }

        int scaledWidth = (int) screenshotScaleDimensions.getWidth();
        int scaledHeight = (int) screenshotScaleDimensions.getHeight();

        Mode mode = Mode.AUTOMATIC;
        if (screenshotScaleStretch) {
            mode = Mode.FIT_EXACT;
        }
        Method method = Method.AUTOMATIC;
        if (screenshotScaleQuality != null) {
            method = screenshotScaleQuality.scalrMethod;
        }
        BufferedImage newImg =
                Scalr.resize(origImg, method, mode, scaledWidth, scaledHeight);

        // Remove alpha layer for formats not supporting it. This prevents
        // some files from having a colored background (instead of transparency)
        // or to not be saved properly (e.g. png to bmp).
        if (EqualsUtil.equalsNoneIgnoreCase(
                screenshotImageFormat, "png", "gif")) {
            BufferedImage fixedImg = new BufferedImage(
                    newImg.getWidth(), newImg.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            fixedImg.createGraphics().drawImage(
                    newImg, 0, 0, Color.WHITE, null);
            newImg = fixedImg;
        }
        return newImg;
    }

    private SystemCommand createPhantomJSCommand(PhantomJSArguments p) {
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(exePath);
        cmdArgs.add("--ssl-protocol=any");
        if (LOG.isDebugEnabled()) {
            cmdArgs.add("--debug=true");
        }
        cmdArgs.add("--ignore-ssl-errors=true");
        cmdArgs.add("--web-security=false");
        cmdArgs.add("--cookies-file="
                + argQuote(p.phantomCookiesFile.toAbsolutePath().toString()));
        cmdArgs.add("--load-images=" + isScreenshotEnabled());
        if (!options.isEmpty()) {
            cmdArgs.addAll(options);
        }
        cmdArgs.add(argQuote(p.phantomScriptFile.toAbsolutePath().toString()));
        cmdArgs.add(argQuote(p.url));                      // phantom.js arg 1
        cmdArgs.add(argQuote(
                p.outFile.toAbsolutePath().toString()));   // phantom.js arg 2
        cmdArgs.add(Integer.toString(renderWaitTime));     // phantom.js arg 3
        cmdArgs.add(Integer.toString(-1));
        cmdArgs.add(p.protocol);                           // phantom.js arg 5
        if (p.phantomScreenshotFile == null) {             // phantom.js arg 6
            cmdArgs.add(argQuote(""));
        } else {
            cmdArgs.add(argQuote(
                    p.phantomScreenshotFile.toAbsolutePath().toString()));
        }
        if (screenshotDimensions == null) {                // phantom.js arg 7
            cmdArgs.add(argQuote(""));
        } else {
            cmdArgs.add(argQuote(
                    screenshotDimensions.getWidth() + "x"
                  + screenshotDimensions.getHeight()));
        }
        cmdArgs.add(Float.toString(screenshotZoomFactor)); // phantom.js arg 8
        cmdArgs.add(Integer.toString(resourceTimeout));    // phantom.js arg 9

        SystemCommand cmd = new SystemCommand(
                cmdArgs.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
        if (LOG.isDebugEnabled()) {
            LOG.debug("Command: {}", cmd);
        }
        return cmd;
    }

    private void performDetection(Doc doc) throws IOException {
        if (fetcherConfig.isForceContentTypeDetection()) {
            ContentType ct = ContentTypeDetector.detect(
                    doc.getContent(), doc.getReference());
            if (ct != null) {
                doc.getMetadata().set(
                        DocMetadata.CONTENT_TYPE, ct.toString());
            }
        }
        if (fetcherConfig.isForceCharsetDetection()) {
            String charset = CharsetUtil.detectCharset(doc.getContent());
            if (StringUtils.isNotBlank(charset)) {
                doc.getMetadata().set(
                        DocMetadata.CONTENT_ENCODING, charset);
            }
        }
    }

    private void validate() {
	    if (StringUtils.isBlank(exePath)) {
	        throw new CollectorException(
	                "PhantomJS execution path is not set.");
	    }
	    if (!new File(exePath).isFile()) {
            throw new CollectorException(
                    "PhantomJS execution path does not exist or is "
                  + "not a valid file: " + new File(exePath).getAbsolutePath());
	    }
        if (StringUtils.isBlank(scriptPath)) {
            throw new CollectorException(
                    "PhantomJS script path is not set.");
        }
        if (!new File(scriptPath).isFile()) {
            throw new CollectorException(
                    "PhantomJS script file does not exist or is not a "
                  + "valid file: " + new File(scriptPath).getAbsolutePath());
        }
        if (StringUtils.isNotBlank(screenshotStorageDiskDir)) {
            File dir = new File(screenshotStorageDiskDir);
            if (dir.exists()) {
                if (!dir.isDirectory()) {
                    throw new CollectorException(
                            "PhantomJS screenshot directory is invalid: "
                          + dir.getAbsolutePath());
                }
            } else if (!dir.mkdirs()) {
                throw new CollectorException(
                        "PhantomJS screenshot directory could not be created: "
                      + dir.getAbsolutePath());
            }
        }
    }
    private String argQuote(String arg) {
        String safeArg = Objects.toString(arg, "");
        if (SystemUtils.IS_OS_WINDOWS) {
            safeArg = StringUtils.strip(safeArg, "\"");
            safeArg = safeArg.replaceAll("((?<!\\^\\^\\^)[\\|\\&])", "^^^$1");
            return "\"" + safeArg + "\"";
        }
        return safeArg;
	}

    private boolean isHTMLByReference(String url) {
        return Pattern.matches(referencePattern, Objects.toString(url, ""));
    }
    private boolean isHTMLByContentType(String contentType) {
        String cleanContentType = StringUtils.trimToEmpty(
                StringUtils.substringBefore(contentType, ";"));
        return Pattern.matches(
                contentTypePattern, cleanContentType);
    }
    private String getContentType(Doc doc) {
        String ct = Objects.toString(doc.getDocInfo().getContentType(), null);
        if (StringUtils.isBlank(ct)) {
            ct = doc.getMetadata().getString(Objects.toString(
                    fetcherConfig.getHeadersPrefix(), "")
                        + HttpHeaders.CONTENT_TYPE);
        }
        return ct;
    }

    @Override
    protected void loadHttpFetcherFromXML(XML xml) {
        setExePath(xml.getString("exePath", exePath));
        setScriptPath(xml.getString("scriptPath", scriptPath));

        setRenderWaitTime(xml.getDurationMillis(
                "renderWaitTime", (long) renderWaitTime).intValue());
        setResourceTimeout(xml.getDurationMillis(
                "resourceTimeout", (long) resourceTimeout).intValue());
        setValidExitCodes(xml.getDelimitedList(
                "validExitCodes", Integer.class, getValidExitCodes()));
        setValidStatusCodes(xml.getDelimitedList(
                "validStatusCodes", Integer.class,
                fetcherConfig.getValidStatusCodes()));
        setNotFoundStatusCodes(xml.getDelimitedList(
                "notFoundStatusCodes", Integer.class,
                fetcherConfig.getNotFoundStatusCodes()));
        setHeadersPrefix(xml.getString("headersPrefix",
                fetcherConfig.getHeadersPrefix()));
        setDetectContentType(
                xml.getBoolean("@detectContentType", fetcherConfig.isForceContentTypeDetection()));
        setDetectCharset(xml.getBoolean("@detectCharset", fetcherConfig.isForceCharsetDetection()));
        setOptions(xml.getDelimitedStringList("options/opt", options));
        setReferencePattern(
                xml.getString("referencePattern", referencePattern));
        setContentTypePattern(
                xml.getString("contentTypePattern", contentTypePattern));

        // Screenshots
        setScreenshotEnabled(xml.getBoolean(
                "@screenshotEnabled", screenshotEnabled));
        setScreenshotScaleQuality(xml.getEnum("screenshotScaleQuality",
                Quality.class, screenshotScaleQuality));
        setScreenshotStorage(xml.getDelimitedEnumList("screenshotStorage",
                Storage.class, screenshotStorage));
        setScreenshotStorageDiskDir(xml.getString("screenshotStorageDiskDir",
                screenshotStorageDiskDir));
        setScreenshotStorageDiskField(xml.getString(
                "screenshotStorageDiskField", screenshotStorageDiskField));
        setScreenshotStorageInlineField(xml.getString(
                "screenshotStorageInlineField",
                screenshotStorageInlineField));
        setScreenshotDimensions(xml.get("screenshotDimensions",
                Dimension.class, screenshotDimensions));
        setScreenshotZoomFactor(xml.getFloat(
                "screenshotZoomFactor", getScreenshotZoomFactor()));
        setScreenshotScaleDimensions(xml.get("screenshotScaleDimensions",
                Dimension.class, screenshotScaleDimensions));
        setScreenshotScaleStretch(xml.getBoolean(
                "screenshotScaleStretch", screenshotScaleStretch));
        setScreenshotImageFormat(xml.getString(
                "screenshotImageFormat", screenshotImageFormat));
        setScreenshotStorageDiskStructure(
                xml.getEnum("screenshotStorageDiskDir/@structure",
                        StorageDiskStructure.class,
                        screenshotStorageDiskStructure));
    }
    @Override
    protected void saveHttpFetcherToXML(XML xml) {
        xml.setAttribute("detectContentType",
                fetcherConfig.isForceContentTypeDetection());
        xml.setAttribute("detectCharset", fetcherConfig.isForceCharsetDetection());
        xml.setAttribute("screenshotEnabled", screenshotEnabled);

        xml.addElement("exePath", exePath);
        xml.addElement("scriptPath", scriptPath);
        xml.addElement("renderWaitTime", renderWaitTime);
        xml.addElement("resourceTimeout", resourceTimeout);
        xml.addDelimitedElementList("validExitCodes", getValidExitCodes());
        xml.addDelimitedElementList(
                "validStatusCodes", fetcherConfig.getValidStatusCodes());
        xml.addDelimitedElementList(
                "notFoundStatusCodes", fetcherConfig.getNotFoundStatusCodes());
        xml.addElement("headersPrefix", fetcherConfig.getHeadersPrefix());
        xml.addElementList("options", "opt", options);
        xml.addElement("referencePattern", referencePattern);
        xml.addElement("contentTypePattern", contentTypePattern);

        // Screenshots
        xml.addElement("screenshotScaleQuality", screenshotScaleQuality);
        xml.addElement("screenshotScaleDimensions", screenshotScaleDimensions);
        xml.addElement(
                "screenshotStorageDiskField", screenshotStorageDiskField);
        xml.addElement("screenshotStorageInlineField",
                screenshotStorageInlineField);
        xml.addElement("screenshotDimensions", screenshotDimensions);
        xml.addElement("screenshotZoomFactor", screenshotZoomFactor);
        xml.addDelimitedElementList("screenshotStorage", screenshotStorage);

        xml.addElement("screenshotStorageDiskDir", screenshotStorageDiskDir)
                .setAttribute("structure", screenshotStorageDiskStructure);

        xml.addElement("screenshotScaleStretch", screenshotScaleStretch);
        xml.addElement("screenshotImageFormat", screenshotImageFormat);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(
                this, other, "contentTypeDetector");
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, "contentTypeDetector");
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE)
                    .setExcludeFieldNames("contentTypeDetector").toString();
    }

    private static class PhantomJSArguments {
        private final String url;
        private final Path phantomTempdir;
        private final Path phantomCookiesFile;
        private final Path phantomScriptFile;
        private final Path phantomScreenshotFile;
        private final Path outFile;
        private final String protocol;

        public PhantomJSArguments(
                PhantomJSDocumentFetcher f, Doc doc) {
            super();
            String ref = doc.getReference();
            this.url = ref;

            this.phantomTempdir = doc.getContent().getCacheDirectory();
            this.phantomScriptFile = Paths.get(f.scriptPath);
            this.phantomCookiesFile = phantomTempdir.resolve("cookies.txt");
            if (f.isScreenshotEnabled()) {
                this.phantomScreenshotFile = phantomTempdir.resolve(
                        Long.toString(TimeIdGenerator.next()) + ".png");
            } else {
                this.phantomScreenshotFile = null;
            }
            // outFile is automatically deleted by framework when done with it.
            this.outFile = phantomTempdir.resolve(
                    Long.toString(TimeIdGenerator.next()));

            String scheme = "http";
            if (doc.getReference().startsWith("https")) {
                scheme = "https";
            }
            this.protocol = scheme;
        }
    }

    //Metadata is expected to be outputed, starting with HEADER: on each line
    private static class CmdOutputGrabber extends InputStreamLineListener {
        private final StringWriter error = new StringWriter();
        private final StringWriter info = new StringWriter();
        private final StringWriter debug = new StringWriter();
        private final Properties metadata;
        private final String headersPrefix;
        private final SystemCommand cmd;
        private int statusCode = -1;
        private String statusText;
        private String contentType;
        private String redirect;
        public CmdOutputGrabber(SystemCommand cmd,
                Properties metadata, String headersPrefix) {
            super();
            this.cmd = cmd;
            this.metadata = metadata;
            this.headersPrefix = headersPrefix;
        }
        @Override
        protected void lineStreamed(String type, String line) {
            if (line.startsWith("HEADER:")) {
                String key = StringUtils.substringBetween(line, "HEADER:", "=");
                String value = StringUtils.substringAfter(line, "=");

                if (StringUtils.isNotBlank(headersPrefix)) {
                    key = headersPrefix + key;
                }
                if (metadata.getString(key) == null) {
                    metadata.add(key, value);
                }
            } else if (line.startsWith("STATUS:")) {
                statusCode = NumberUtils.toInt(
                        StringUtils.substringAfter(line, "STATUS:"), -1);
            } else if (line.startsWith("STATUSTEXT:")) {
                statusText = StringUtils.substringAfter(line, "STATUSTEXT:");
            } else if (line.startsWith("CONTENTTYPE:")) {
                contentType = StringUtils.substringAfter(line, "CONTENTTYPE:");
            } else if (line.contains("[DEBUG]")) {
                debug.write("\n  " + line);

            // abort command on possible errors that hang the process:
            // https://github.com/ariya/phantomjs/issues/10687
            } else if (line.startsWith("ReferenceError:")) {
                error.write("\n  " + line);
                cmd.abort();
            // Log errors not matching above as real errors
            } else if (ExecUtil.STDERR.equalsIgnoreCase(type)) {
                    error.write("\n  " + line);
            // consider everythign else as INFO
            } else {
                info.write("\n  " + line);
            }
        }
        public String getError() {
            return error.toString();
        }
        public String getInfo() {
            return info.toString();
        }
        public String getDebug() {
            return debug.toString();
        }
        public int getStatusCode() {
            return statusCode;
        }
        public String getStatusText() {
            return statusText;
        }
        public String getContentType() {
            return contentType;
        }
        public String getRedirect() {
            return redirect;
        }
    }
}