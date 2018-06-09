/* Copyright 2017 Norconex Inc.
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.core.doc.CollectorMetadata;
import com.norconex.collector.http.client.IHttpClientFactory;
import com.norconex.collector.http.client.impl.GenericHttpClientFactory;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.collector.http.processor.impl.ScaledImage;
import com.norconex.collector.http.redirect.RedirectStrategyWrapper;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.exec.ExecUtil;
import com.norconex.commons.lang.exec.SystemCommand;
import com.norconex.commons.lang.exec.SystemCommandException;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.InputStreamLineListener;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.util.CharsetUtil;

//TODO consider sharing some image-processing logic with FeaturedImageProcessor.

/**
 * <p>
 * An alternative to the {@link GenericDocumentFetcher} which relies on an 
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
 * scalable and may be less stable. The use of {@link GenericDocumentFetcher}
 * should be preferred whenever possible. Use at your own risk. 
 * Use PhantomJS 2.1 (or possibly higher).
 * </p>
 * 
 * <h3>Handling of non-HTML Pages</h3>
 * <p>
 * It is usually only useful to use PhantomJS for HTML pages with JavaScript.
 * Other types of documents are fetched using an instance of 
 * {@link GenericDocumentFetcher}    
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
 * configure a metadata fetcher (such as {@link GenericMetadataFetcher}).  This
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
 * <h3>How to maintain HTTP sessions</h3>
 * <p>
 * Normally, the HTTP crawler is meant to be used with Apache {@link HttpClient}
 * which is usually configured using {@link GenericHttpClientFactory}. Doing so
 * ensures HTTP sessions are maintained between each URL invocation.  This is 
 * necessary for web sites expecting cookies or session information to be 
 * carried over each requests as part of HTTP headers.  
 * Unfortunately, session information
 * is not maintained between requests when invoking PhantomJS for each URLs.
 * This means Apache {@link HttpClient} is not used at all and configuring 
 * {@link IHttpClientFactory} has no effect for fetching documents. As a result,
 * you may have trouble with specific web sites.
 * </p>
 * <p>
 * If that's the case, you may want to try adding the
 * {@link HttpClientProxyCollectorListener} to your collector configuration. 
 * This will start an HTTP proxy and force PhantomJS to use it.  That proxy 
 * will use {@link HttpClient} to fetch documents as you would normally expect
 * and you can full advantage of {@link GenericHttpClientFactory} (or
 * your own implementation of {@link IHttpClientFactory}). Using a proxy
 * with secure (<code>https</code>) requests may not always give expected 
 * results either (e.g., screenshots maybe broken). If you run into issues
 * with a given site, try both approaches and pick the one that works best for
 * you.
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
 *        &lt;!-- You have have multiple opt tags --&gt;
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
 * @see HttpClientProxyCollectorListener
 * @since 2.7.0
 */
public class PhantomJSDocumentFetcher 
        implements IHttpDocumentFetcher, IXMLConfigurable {

    private static final Logger LOG = LogManager.getLogger(
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
        private Quality(Method scalrMethod) {
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
    
    /*default*/ static final int[] DEFAULT_VALID_STATUS_CODES = new int[] {
            HttpStatus.SC_OK,
    };
    /*default*/ static final int[] DEFAULT_NOT_FOUND_STATUS_CODES = new int[] {
            HttpStatus.SC_NOT_FOUND,
    };
    
    public static final String COLLECTOR_PHANTOMJS_SCREENSHOT_PATH = 
            CollectorMetadata.COLLECTOR_PREFIX + "phantomjs-screenshot-path";
    public static final String COLLECTOR_PHANTOMJS_SCREENSHOT_INLINE = 
            CollectorMetadata.COLLECTOR_PREFIX + "phantomjs-screenshot-inline";    
    
    
    private String exePath;
    private String scriptPath = DEFAULT_SCRIPT_PATH;
    private int renderWaitTime = DEFAULT_RENDER_WAIT_TIME;
    private int resourceTimeout = -1;
    
    private String[] options;
    private int[] validStatusCodes;
    private int[] notFoundStatusCodes = 
            PhantomJSDocumentFetcher.DEFAULT_NOT_FOUND_STATUS_CODES;
    private String headersPrefix;
    private boolean detectContentType;
    private boolean detectCharset;
    private ContentTypeDetector contentTypeDetector = new ContentTypeDetector();    
    
    private String contentTypePattern = DEFAULT_CONTENT_TYPE_PATTERN;
    private String referencePattern;

    private final GenericDocumentFetcher genericFetcher = 
            new GenericDocumentFetcher();

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
    private Storage[] screenshotStorage = 
            new Storage[] { DEFAULT_SCREENSHOT_STORAGE };
    private StorageDiskStructure screenshotStorageDiskStructure = 
            StorageDiskStructure.DATETIME;
    private Quality screenshotScaleQuality = Quality.AUTO;
    
    private boolean initialized;
    
    public PhantomJSDocumentFetcher() {
        this(PhantomJSDocumentFetcher.DEFAULT_VALID_STATUS_CODES);
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
    public String[] getOptions() {
        return options;
    }
    public void setOptions(String... options) {
        this.options = options;
    }
    /**
     * Gets the screenshot directory when storage is "disk". 
     * @return screenshot directory
     * @deprecated Since 2.8.0, use {@link #getScreenshotStorageDiskDir()}
     */
    @Deprecated
    public String getScreenshotDir() {
        return getScreenshotStorageDiskDir();
    }
    /**
     * Gets the screenshot directory when storage is "disk".
     * @param screenshotDir screenshot directory
     * @deprecated Since 2.8.0, use {@link #setScreenshotStorageDiskDir(String)}
     */
    @Deprecated
    public void setScreenshotDir(String screenshotDir) {
        setScreenshotStorageDiskDir(screenshotDir);
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
    public int[] getValidStatusCodes() {
        return ArrayUtils.clone(validStatusCodes);
    }
    public final void setValidStatusCodes(int... validStatusCodes) {
        this.validStatusCodes = ArrayUtils.clone(validStatusCodes);
        genericFetcher.setValidStatusCodes(validStatusCodes);
    }
    public int[] getNotFoundStatusCodes() {
        return ArrayUtils.clone(notFoundStatusCodes);
    }
    public final void setNotFoundStatusCodes(int... notFoundStatusCodes) {
        this.notFoundStatusCodes = ArrayUtils.clone(notFoundStatusCodes);
        genericFetcher.setNotFoundStatusCodes(notFoundStatusCodes);
    }
    public String getHeadersPrefix() {
        return headersPrefix;
    }
    public void setHeadersPrefix(String headersPrefix) {
        this.headersPrefix = headersPrefix;
        genericFetcher.setHeadersPrefix(headersPrefix);
    }
    public boolean isDetectContentType() {
        return detectContentType;
    }
    public void setDetectContentType(boolean detectContentType) {
        this.detectContentType = detectContentType;
        genericFetcher.setDetectContentType(detectContentType);
    }
    public boolean isDetectCharset() {
        return detectCharset;
    }
    public void setDetectCharset(boolean detectCharset) {
        this.detectCharset = detectCharset;
        genericFetcher.setDetectCharset(detectCharset);
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
     * @return storage mechanisms
     * @since 2.8.0
     */
    public Storage[] getScreenshotStorage() {
        return screenshotStorage;
    }
    /**
     * Sets the screenshot storage mechanisms.
     * @param screenshotStorage storage mechanisms
     * @since 2.8.0
     */
    public void setScreenshotStorage(Storage... screenshotStorage) {
        this.screenshotStorage = screenshotStorage;
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
	public HttpFetchResponse fetchDocument(
	        HttpClient httpClient, HttpDocument doc) {

        init();
        validate();

        // If there is a reference pattern and it does not match, use generic
        if (StringUtils.isNotBlank(referencePattern)
                && !isHTMLByReference(doc.getReference())) {
            LOG.debug("URL does not match reference pattern. "
                    + "Using GenericDocumentFetcher for: "
                    + doc.getReference());
            return genericFetcher.fetchDocument(httpClient, doc);
        }
        // If content type is known and ct pattern does not match, use generic
        String contentType = getContentType(doc);
        if (StringUtils.isNotBlank(contentTypePattern)
                && StringUtils.isNotBlank(contentType)
                && !isHTMLByContentType(contentType)) {
            LOG.debug("Content type (" + contentType
                    + ") known before fetching and does not "
                    + "match pattern. Using GenericDocumentFetcher for: "
                    + doc.getReference());
            return genericFetcher.fetchDocument(httpClient, doc);
        }
        
        // Fetch using PhantomJS
        try {
            return fetchPhantomJSDocument(httpClient, doc);
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
        LOG.info("PhantomJS screenshot enabled: " + screenshotEnabled);
        initialized = true;
    }

    private HttpFetchResponse fetchPhantomJSDocument(
                HttpClient httpClient, HttpDocument doc) 
                        throws IOException, SystemCommandException {
        
        PhantomJSArguments p = new PhantomJSArguments(this, doc);
	    SystemCommand cmd = createPhantomJSCommand(p, httpClient);
	    
	    CmdOutputGrabber output = new CmdOutputGrabber(
	            cmd, doc.getMetadata(), getHeadersPrefix());
	    cmd.addErrorListener(output);
	    cmd.addOutputListener(output);
	    
	    int exit = cmd.execute();

        int statusCode = output.getStatusCode();
        String reason = output.getStatusText();

        // set Content-Type HTTP metadata obtained from CONTENTTYPE output
        // if not obtained via regular headers
        if (!doc.getMetadata().containsKey(HttpMetadata.HTTP_CONTENT_TYPE)
                && StringUtils.isNotBlank(output.getContentType())) {
            doc.getMetadata().setString(HttpMetadata.HTTP_CONTENT_TYPE, 
                    output.getContentType());
        }

        if (StringUtils.isNotBlank(output.getError())) {
            LOG.error("PhantomJS:" + output.getError());
        }
        if (StringUtils.isNotBlank(output.getInfo())) {
            LOG.info("PhantomJS:" + output.getInfo());
        }
        if (StringUtils.isNotBlank(output.getDebug())) {
            LOG.debug("PhantomJS:" + output.getDebug());
        }
        
        if (StringUtils.isNotBlank(output.getRedirect())) {
            RedirectStrategyWrapper.setRedirectURL(output.getRedirect());
        }
        
        // deal with screenshot regardless whether execution failed or not
        handleScreenshot(p, doc);
        
        // VALID response
        if (exit == 0 && ArrayUtils.contains(validStatusCodes, statusCode)) {
            //--- Fetch body
            doc.setContent(doc.getContent().newInputStream(p.outFile));
            //read a copy to force caching
            IOUtils.copy(doc.getContent(), new NullOutputStream());

            performDetection(doc);
            
            String contentType = getContentType(doc);
            if (!isHTMLByContentType(contentType)) {
                LOG.debug("Not a matching content type (" + contentType
                    + ")  after download, re-downloading with "
                    + "GenericDocumentFetcher for: " + doc.getReference());
                return genericFetcher.fetchDocument(httpClient, doc);
            }
            
            return new HttpFetchResponse(
                    HttpCrawlState.NEW, statusCode, reason);
        }
        
        // INVALID response
        if (LOG.isTraceEnabled()) {
            LOG.trace("Rejected response content: "
                    + FileUtils.readFileToString(
                            p.outFile, StandardCharsets.UTF_8));
        }
        if (ArrayUtils.contains(notFoundStatusCodes, statusCode)) {
            return new HttpFetchResponse(
                    HttpCrawlState.NOT_FOUND, statusCode, reason);
        }
        if (exit != 0) {
            return new HttpFetchResponse(CrawlState.BAD_STATUS, exit, 
                    "PhantomJS execution failed with exit code " + exit);
        }
        LOG.debug("Unsupported HTTP Response: " + reason);
        return new HttpFetchResponse(
                CrawlState.BAD_STATUS, statusCode, reason);
	}
    
    private void handleScreenshot(PhantomJSArguments p, HttpDocument doc) {

        // must be enabled
        if (!isScreenshotEnabled()) {
            return;
        }
        
        if (!p.phantomScreenshotFile.isFile()) {
            LOG.error("Screenshot file not created for " + doc.getReference());
            return;
        }

        BufferedImage bi = null;
        try {
            bi = ImageIO.read(p.phantomScreenshotFile);
        } catch (IOException e) {
            LOG.error("Could not load screenshot for: \"" + doc.getReference()
                    + "\". It was saved here: " 
                    + p.phantomScreenshotFile.getAbsolutePath(), e);
            return;
        }
        try {
            FileUtil.delete(p.phantomScreenshotFile);
        } catch (IOException e) {
            LOG.warn("Could not delete temp screenshot file: "
                    + p.phantomScreenshotFile, e);
        }
        if (bi == null) {
            LOG.debug("Image is null for: " + doc.getReference());
            return;
        }
        
        Dimension dim = new Dimension(bi.getWidth(), bi.getHeight());
        bi = scale(bi);
        
        ScaledImage img = new ScaledImage(doc.getReference(), dim, bi);
        
        try {
            if (ArrayUtils.contains(screenshotStorage, Storage.INLINE)) {
                doc.getMetadata().addString(
                        Objects.toString(screenshotStorageInlineField, 
                                COLLECTOR_PHANTOMJS_SCREENSHOT_INLINE), 
                        img.toHTMLInlineString(screenshotImageFormat));
            }
            if (ArrayUtils.contains(screenshotStorage, Storage.DISK)) {
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
                doc.getMetadata().addString(
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
    
    private SystemCommand createPhantomJSCommand(
            PhantomJSArguments p, HttpClient httpClient) {
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(exePath);
        cmdArgs.add("--ssl-protocol=any");
        if (LOG.isDebugEnabled()) {
            cmdArgs.add("--debug=true");
        }
        cmdArgs.add("--ignore-ssl-errors=true");
        cmdArgs.add("--web-security=false");
        cmdArgs.add("--cookies-file=" 
                + argQuote(p.phantomCookiesFile.getAbsolutePath()));
        cmdArgs.add("--load-images=" + isScreenshotEnabled());
        // Configure for HttpClient proxy if used.
        if (HttpClientProxy.isStarted()) {
            cmdArgs.add("--proxy=" + HttpClientProxy.getProxyHost());
            
            cmdArgs.add("--proxy-auth=bindId:" 
                    + HttpClientProxy.getId(httpClient));
        }
        if (ArrayUtils.isNotEmpty(options)) {
            cmdArgs.addAll(Arrays.asList(options));
        }
        cmdArgs.add(argQuote(p.phantomScriptFile.getAbsolutePath()));
        cmdArgs.add(argQuote(p.url));                      // phantom.js arg 1
        cmdArgs.add(argQuote(p.outFile.getAbsolutePath()));// phantom.js arg 2
        cmdArgs.add(Integer.toString(renderWaitTime));     // phantom.js arg 3
        if (HttpClientProxy.isStarted()) {                 // phantom.js arg 4
            cmdArgs.add(Integer.toString(HttpClientProxy.getId(httpClient)));
        } else {
            cmdArgs.add(Integer.toString(-1));
        }
        cmdArgs.add(p.protocol);                           // phantom.js arg 5
        if (p.phantomScreenshotFile == null) {             // phantom.js arg 6
            cmdArgs.add(argQuote(""));
        } else {
            cmdArgs.add(argQuote(p.phantomScreenshotFile.getAbsolutePath()));
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
            LOG.debug("Command: " + cmd);
        }
        return cmd; 
    }
    
    //TODO Copied from GenericDocumentFetcher... should move to util class?
    private void performDetection(HttpDocument doc) throws IOException {
        if (detectContentType) {
            ContentType ct = contentTypeDetector.detect(
                    doc.getContent(), doc.getReference());
            if (ct != null) {
                doc.getMetadata().setString(
                        HttpMetadata.COLLECTOR_CONTENT_TYPE, ct.toString());
            }
        }
        if (detectCharset) {
            String charset = CharsetUtil.detectCharset(doc.getContent());
            if (StringUtils.isNotBlank(charset)) {
                doc.getMetadata().setString(
                        HttpMetadata.COLLECTOR_CONTENT_ENCODING, charset);
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
    private String getContentType(HttpDocument doc) {
        String ct = Objects.toString(doc.getContentType(), null);
        if (StringUtils.isBlank(ct)) {
            ct = doc.getMetadata().getString(Objects.toString(headersPrefix, "")
                    + HttpMetadata.HTTP_CONTENT_TYPE);
        }
        return ct;
    }

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = XMLConfigurationUtil.newXMLConfiguration(in);
        setExePath(xml.getString("exePath", getExePath()));
        setScriptPath(xml.getString("scriptPath", getScriptPath()));
        setRenderWaitTime((int) XMLConfigurationUtil.getDuration(
                xml, "renderWaitTime", getRenderWaitTime()));
        setResourceTimeout((int) XMLConfigurationUtil.getDuration(
                xml, "resourceTimeout", getResourceTimeout()));
        
        String oldDir = xml.getString("screenshotDir", null);
        if (StringUtils.isNotBlank(oldDir)) {
            LOG.warn("\"screenshotDir\" has been deprecated in favor of "
                   + "\"screenshotStorageDiskDir\".");
        }
        setValidStatusCodes(XMLConfigurationUtil.getCSVIntArray(
                xml, "validStatusCodes", getValidStatusCodes()));
        setNotFoundStatusCodes(XMLConfigurationUtil.getCSVIntArray(
                xml, "notFoundStatusCodes", getNotFoundStatusCodes()));
        setHeadersPrefix(xml.getString("headersPrefix", getHeadersPrefix()));
        setDetectContentType(
                xml.getBoolean("[@detectContentType]", isDetectContentType()));
        setDetectCharset(xml.getBoolean("[@detectCharset]", isDetectCharset()));        
        
        String[] opts = xml.getStringArray("options.opt");
        if (ArrayUtils.isNotEmpty(opts)) {
            setOptions(opts);
        }
        setReferencePattern(
                xml.getString("referencePattern", getReferencePattern()));
        setContentTypePattern(
                xml.getString("contentTypePattern", getContentTypePattern()));
        
        // Screenshots
        setScreenshotEnabled(xml.getBoolean(
                "[@screenshotEnabled]", isScreenshotEnabled()));

        if (xml.containsKey("screenshotScaleQuality")) {
            String xmlQuality = xml.getString("screenshotScaleQuality", null);
            if (StringUtils.isNotBlank(xmlQuality)) {
                setScreenshotScaleQuality(Quality.valueOf(xmlQuality.toUpperCase()));
            } else {
                setScreenshotScaleQuality((Quality) null);
            }
        }
        
        if (xml.containsKey("screenshotStorage")) {
            String[] xmlStorages = XMLConfigurationUtil.getCSVStringArray(
                    xml, "screenshotStorage");
            if (ArrayUtils.isNotEmpty(xmlStorages)) {
                Storage[] storages = new Storage[xmlStorages.length];
                for (int i = 0; i < xmlStorages.length; i++) {
                    String xmlStorage = xmlStorages[i];
                    storages[i] = Storage.valueOf(xmlStorage.toUpperCase());
                }
                setScreenshotStorage(storages);
            } else {
                setScreenshotStorage((Storage) null);
            }
        }
        
        String newDir = xml.getString("screenshotStorageDiskDir", 
                getScreenshotStorageDiskDir());
        if (StringUtils.isBlank(newDir)) {
            newDir = oldDir;
        }
        setScreenshotStorageDiskDir(newDir);

        setScreenshotStorageDiskField(xml.getString(
                "screenshotStorageDiskField", getScreenshotStorageDiskField()));
        setScreenshotStorageInlineField(xml.getString(
                "screenshotStorageInlineField", 
                getScreenshotStorageInlineField()));
        setScreenshotDimensions(XMLConfigurationUtil.getNullableDimension(
                xml, "screenshotDimensions", getScreenshotDimensions()));
        setScreenshotZoomFactor(xml.getFloat(
                "screenshotZoomFactor", getScreenshotZoomFactor()));
        setScreenshotScaleDimensions(XMLConfigurationUtil.getNullableDimension(
                xml, "screenshotScaleDimensions", 
                getScreenshotScaleDimensions()));
        setScreenshotScaleStretch(xml.getBoolean(
                "screenshotScaleStretch", isScreenshotScaleStretch()));
        setScreenshotImageFormat(xml.getString(
                "screenshotImageFormat", getScreenshotImageFormat()));

        if (xml.containsKey("screenshotStorageDiskDir[@structure]")) {
            String xmlStructure = 
                    xml.getString("screenshotStorageDiskDir[@structure]", null);
            if (StringUtils.isNotBlank(xmlStructure)) {
                setScreenshotStorageDiskStructure(StorageDiskStructure.valueOf(
                        xmlStructure.toUpperCase()));
            } else {
                setScreenshotStorageDiskStructure((StorageDiskStructure) null);
            }
        }
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);         
            writer.writeStartElement("documentFetcher");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeAttributeBoolean(
                    "detectContentType", isDetectContentType());
            writer.writeAttributeBoolean("detectCharset", isDetectCharset());
            writer.writeAttributeBoolean(
                    "screenshotEnabled", isScreenshotEnabled());
            
            writer.writeElementString("exePath", exePath);
            writer.writeElementString("scriptPath", scriptPath);
            writer.writeElementInteger("renderWaitTime", renderWaitTime);
            if (resourceTimeout != -1) {
                writer.writeElementInteger("resourceTimeout", resourceTimeout);
            }
            writer.writeElementString("validStatusCodes", 
                    StringUtils.join(validStatusCodes, ','));
            writer.writeElementString("notFoundStatusCodes", 
                    StringUtils.join(notFoundStatusCodes, ','));
            writer.writeElementString("headersPrefix", headersPrefix);
            if (ArrayUtils.isNotEmpty(options)) {
                writer.writeStartElement("options");
                for (String arg : options) {
                    writer.writeElementString("opt", arg);
                }
                writer.writeEndElement();
            }
            writer.writeElementString("referencePattern", referencePattern);
            writer.writeElementString("contentTypePattern", contentTypePattern);

            // Screenshots
            writer.writeElementString("screenshotScaleQuality", 
                    getScreenshotScaleQuality() != null 
                    ? getScreenshotScaleQuality().toString().toLowerCase() 
                    : null, true);
            writer.writeElementDimension("screenshotScaleDimensions", 
                    getScreenshotScaleDimensions(), true);
            writer.writeElementString(
                    "screenshotStorageDiskField", screenshotStorageDiskField);
            writer.writeElementString("screenshotStorageInlineField", 
                    screenshotStorageInlineField);
            writer.writeElementDimension(
                    "screenshotDimensions", screenshotDimensions, true);
            writer.writeElementFloat(
                    "screenshotZoomFactor", screenshotZoomFactor);

            Storage[] storages = getScreenshotStorage();
            if (ArrayUtils.isNotEmpty(storages)) {
                String[] xmlStorages = new String[storages.length];
                for (int i = 0; i < storages.length; i++) {
                    if (storages[i] != null) {
                        xmlStorages[i] = storages[i].toString().toLowerCase();
                    }
                }
                writer.writeElementString("screenshotStorage", 
                        StringUtils.join(xmlStorages, ','), true);
            }
            
            writer.writeStartElement("screenshotStorageDiskDir");
            String structure = null;
            if (getScreenshotStorageDiskStructure() != null) {
                structure = getScreenshotStorageDiskStructure()
                        .toString().toLowerCase();
            }
            writer.writeAttribute(
                    "structure", StringUtils.trimToEmpty(structure));
            writer.writeCharacters(StringUtils.trimToEmpty(
                    getScreenshotStorageDiskDir()));
            writer.writeEndElement();
            
            writer.writeElementBoolean(
                    "screenshotScaleStretch", isScreenshotScaleStretch());
            writer.writeElementString(
                    "screenshotImageFormat", getScreenshotImageFormat());
            
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
    
    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof PhantomJSDocumentFetcher)) {
            return false;
        }
        PhantomJSDocumentFetcher castOther = (PhantomJSDocumentFetcher) other;
        return new EqualsBuilder()
                .append(exePath, castOther.exePath)
                .append(scriptPath, castOther.scriptPath)
                .append(renderWaitTime, castOther.renderWaitTime)
                .append(resourceTimeout, castOther.resourceTimeout)
                .append(options, castOther.options)
                .append(referencePattern, castOther.referencePattern)
                .append(contentTypePattern, castOther.contentTypePattern)
                .append(validStatusCodes, castOther.validStatusCodes)
                .append(notFoundStatusCodes, castOther.notFoundStatusCodes)
                .append(headersPrefix, castOther.headersPrefix)
                .append(detectContentType, castOther.detectContentType)
                .append(detectCharset, castOther.detectCharset)
                .append(screenshotEnabled, castOther.screenshotEnabled)
                .append(screenshotStorageDiskDir, 
                        castOther.screenshotStorageDiskDir)
                .append(screenshotStorageDiskField, 
                        castOther.screenshotStorageDiskField)
                .append(screenshotStorageInlineField, 
                        castOther.screenshotStorageInlineField)
                .append(screenshotDimensions, castOther.screenshotDimensions)
                .append(screenshotZoomFactor, castOther.screenshotZoomFactor)
                .append(screenshotScaleDimensions, 
                        castOther.screenshotScaleDimensions)
                .append(screenshotScaleStretch, 
                        castOther.screenshotScaleStretch)
                .append(screenshotImageFormat, castOther.screenshotImageFormat)
                .append(screenshotStorage, castOther.screenshotStorage)
                .append(screenshotStorageDiskStructure, 
                        castOther.screenshotStorageDiskStructure)
                .append(screenshotScaleQuality, 
                        castOther.screenshotScaleQuality)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(exePath)
                .append(scriptPath)
                .append(renderWaitTime)
                .append(resourceTimeout)
                .append(options)
                .append(referencePattern)
                .append(contentTypePattern)
                .append(validStatusCodes)
                .append(notFoundStatusCodes)
                .append(headersPrefix)
                .append(detectContentType)
                .append(detectCharset)
                .append(screenshotEnabled)
                .append(screenshotStorageDiskDir)
                .append(screenshotStorageDiskField)
                .append(screenshotStorageInlineField)
                .append(screenshotDimensions)
                .append(screenshotZoomFactor)
                .append(screenshotScaleDimensions)
                .append(screenshotScaleStretch)
                .append(screenshotImageFormat)
                .append(screenshotStorage)
                .append(screenshotStorageDiskStructure)
                .append(screenshotScaleQuality)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("exePath", exePath)
                .append("scriptPath", scriptPath)
                .append("renderWaitTime", renderWaitTime)
                .append("resourceTimeout", resourceTimeout)
                .append("options", options)
                .append("referencePattern", referencePattern)
                .append("contentTypePattern", contentTypePattern)
                .append("validStatusCodes", validStatusCodes)
                .append("notFoundStatusCodes", notFoundStatusCodes)
                .append("headersPrefix", headersPrefix)
                .append("detectContentType", detectContentType)
                .append("detectCharset", detectCharset)
                .append("screenshotEnabled", screenshotEnabled)
                .append("screenshotStorageDiskDir", screenshotStorageDiskDir)
                .append("screenshotStorageDiskField", 
                        screenshotStorageDiskField)
                .append("screenshotStorageInlineField", 
                        screenshotStorageInlineField)
                .append("screenshotDimensions", screenshotDimensions)
                .append("screenshotZoomFactor", screenshotZoomFactor)
                .append("screenshotScaleDimensions", screenshotScaleDimensions)
                .append("screenshotScaleStretch", screenshotScaleStretch)
                .append("screenshotImageFormat", screenshotImageFormat)
                .append("screenshotStorage", screenshotStorage)
                .append("screenshotStorageDiskStructure", 
                        screenshotStorageDiskStructure)
                .append("screenshotScaleQuality", screenshotScaleQuality)
                .toString();
    }    

    private static class PhantomJSArguments {
        private final String url;
        private final File phantomTempdir;
        private final File phantomCookiesFile;
        private final File phantomScriptFile;
        private final File phantomScreenshotFile;
        private final File outFile;
        private final String protocol;
        
        public PhantomJSArguments(
                PhantomJSDocumentFetcher f, HttpDocument doc) {
            super();
            String ref = doc.getReference();
            if (HttpClientProxy.isStarted()) {
                ref = ref.replaceFirst("^https", "http");
            }
            this.url = ref;

            this.phantomTempdir = doc.getContent().getCacheDirectory();
            this.phantomScriptFile = new File(f.scriptPath);
            this.phantomCookiesFile = new File(phantomTempdir, "cookies.txt");
            if (f.isScreenshotEnabled()) {
                this.phantomScreenshotFile = new File(phantomTempdir, 
                        Long.toString(TimeIdGenerator.next()) + ".png");
            } else {
                this.phantomScreenshotFile = null;
            }
            // outFile is automatically deleted by framework when done with it.
            this.outFile = new File(phantomTempdir, 
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
        private final HttpMetadata metadata;
        private final String headersPrefix;
        private final SystemCommand cmd;
        private int statusCode = -1;
        private String statusText;
        private String contentType;
        private String redirect;
        public CmdOutputGrabber(SystemCommand cmd, 
                HttpMetadata metadata, String headersPrefix) {
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
                
                // Redirect hack
                if (HttpClientProxy.KEY_PROXY_REDIRECT.equals(key)) {
                    this.redirect = value;
                } else if (StringUtils.isNotBlank(headersPrefix)) {
                    key = headersPrefix + key;
                }
                if (metadata.getString(key) == null) {
                    metadata.addString(key, value);
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