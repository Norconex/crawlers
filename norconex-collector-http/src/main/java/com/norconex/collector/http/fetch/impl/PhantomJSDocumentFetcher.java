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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.http.client.IHttpClientFactory;
import com.norconex.collector.http.client.impl.GenericHttpClientFactory;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.fetch.IHttpDocumentFetcher;
import com.norconex.collector.http.redirect.RedirectStrategyWrapper;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.IStreamListener;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;
import com.norconex.jef4.exec.ExecUtils;
import com.norconex.jef4.exec.SystemCommand;

/**
 * <p>
 * An alternative to the {@link GenericDocumentFetcher} which relies on an 
 * external <a href="http://phantomjs.org/">PhantomJS</a> installation
 * to fetch web pages.  While less efficient, this implementation is meant
 * to provide some way to crawl sites making heavy use of JavaScript to render
 * their pages. This class tells the PhantomJS headless browser to wait a certain 
 * amount of time for the page to load extra content via Ajax requests before
 * grabbing all loaded HTML. 
 * </p>
 * 
 * <h3>Experimental</h3>
 * <p>
 * Relying on an external software to fetch pages is slower and not as 
 * scalable and may be less stable. The use of {@link GenericDocumentFetcher}
 * should be preferred whenever possible. Use at your own risk. 
 * Use PhantomJS 2.1 (or possibly higher).
 * </p>
 * 
 * <h3>Handling of non-HTML Pages</h3>
 * <p>
 * With this document fetcher, PhantomJS is only used to fetch HTML documents.
 * Other types of documents are fetched using an instance of 
 * {@link GenericDocumentFetcher}. To find out if we are dealing with HTML
 * documents, it first looks for a URL extension matching one of (case-insensitive):  
 * <pre>
 * html, htm, shtml, asp, pl, cgi, php
 * </pre>
 * <p>
 * If there are no matches, it then relies on the document content type. To help
 * it do so, it is recommended you configure a metadata fetcher (such as 
 * {@link GenericMetadataFetcher}).  
 * Otherwise, the content type will be available only after the document will 
 * have been downloaded by PhantomJS.  If not an HTML document at that point,
 * it will be re-downloaded again with the generic document fetcher. 
 * By default, these content-types are considered HTML:
 * </p>
 * <pre>
 * text/html, application/xhtml+xml, vnd.wap.xhtml+xml, x-asp
 * </pre>
 * <p>
 * Both extensions and content types can be overwritten with
 * {@link #setExtensions(String...)} and {@link #setContentTypes(ContentType...)}
 * respectively.
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
 * 
 * <h3>XML configuration usage:</h3>
 * 
 * <pre>
 *  &lt;documentFetcher  
 *      class="com.norconex.collector.http.fetch.impl.PhantomDocumentFetcher"&gt;
 *      &lt;exePath&gt;(path to PhantomJS executable)&lt;/exePath&gt;
 *      &lt;scriptPath&gt;
 *          (Optional path to a PhantomJS script. Defaults to extra/phantom.js)
 *      &lt;/scriptPath&gt;
 *      &lt;renderWaitTime&gt;
 *          (Milliseconds to wait for a page to load. Defaults to 3000.)
 *      &lt;/renderWaitTime&gt;
 *      &lt;options&gt;
 *        &lt;opt&gt;(optional extra PhantomJS command-line option)&lt;/opt&gt;
 *        &lt;!-- You have have multiple opt tags --&gt;
 *      &lt;/options&gt;
 *      &lt;extensions&gt;
 *          (CSV list of file extensions found in URLs, if any, for which to use 
 *           PhantomJS browser. Non-matching content types will use the 
 *           GenericDocumentFetcher Leave blank or remove this tag to use defaults.)
 *      &lt;/extensions&gt;      
 *      &lt;contentTypes&gt;
 *          (CSV list of content types for which to use PhantomJS browser.
 *           Non-matching content types will use the GenericDocumentFetcher
 *           Leave blank or remove this tag to use defaults.)
 *      &lt;/contentTypes&gt;      
 *      &lt;screenshotDir&gt;
 *          (optional path where to save screenshots)
 *      &lt;/screenshotDir&gt;
 *      &lt;screenshotDimensions&gt;
 *          (Size of the browser page area to capture: [width]x[height].
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
 *      &lt;validStatusCodes&gt;(defaults to 200)&lt;/validStatusCodes&gt;
 *      &lt;notFoundStatusCodes&gt;(defaults to 404)&lt;/notFoundStatusCodes&gt;
 *      &lt;headersPrefix&gt;(string to prefix headers)&lt;/headersPrefix&gt;
 *  &lt;/documentFetcher&gt;
 * </pre>
 * <p>
 * The "validStatusCodes" and "notFoundStatusCodes" elements expect a 
 * coma-separated list of HTTP response code.  If a code is added in both
 * elements, the valid list takes precedence.
 * </p>
 * 
 * <h3>Example:</h3>
 * <p>
 * The following configures HTTP Collector to use PhantomJS with a 
 * proxy to use HttpClient.
 * </p>
 * <pre>
 *  &lt;httpcollector id="MyHttpCollector"&gt;
 *    ...
 *    &lt;crawlers&gt;
 *      &lt;crawler id="MyCrawler"&gt;
 *        ...
 *        &lt;documentFetcher class="com.norconex.collector.http.fetch.impl.PhantomDocumentFetcher"&gt;
 *          &lt;exePath&gt;/path/to/phantomjs.exe&lt;/exePath&gt;
 *          &lt;renderWaitTime&gt;5000&lt;/renderWaitTime&gt;
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
    
    public static final String DEFAULT_SCRIPT_PATH = "extra/phantom.js";
    public static final int DEFAULT_RENDER_WAIT_TIME = 3000;
    public static final float DEFAULT_SCREENSHOT_ZOOM_FACTOR = 1.0f;
    
    /*default*/ static final int[] DEFAULT_VALID_STATUS_CODES = new int[] {
            HttpStatus.SC_OK,
    };
    /*default*/ static final int[] DEFAULT_NOT_FOUND_STATUS_CODES = new int[] {
            HttpStatus.SC_NOT_FOUND,
    };
    private static final ContentType[] DEFAULT_CONTENT_TYPES = 
            new ContentType[] {
        ContentType.HTML,
        ContentType.valueOf("application/xhtml+xml"),
        ContentType.valueOf("vnd.wap.xhtml+xml"),
        ContentType.valueOf("x-asp"),
    };
    private static final String[] DEFAULT_EXTENSIONS = 
            new String[] { "html", "htm", "shtml", "asp", "pl", "cgi", "php" };
        
    private String exePath;
    private String scriptPath = DEFAULT_SCRIPT_PATH;
    private int renderWaitTime = DEFAULT_RENDER_WAIT_TIME;
    private String[] options;
    private String screenshotDir;
    private String screenshotDimensions;
    private float screenshotZoomFactor = DEFAULT_SCREENSHOT_ZOOM_FACTOR;
    
    private int[] validStatusCodes;
    private int[] notFoundStatusCodes = 
            PhantomJSDocumentFetcher.DEFAULT_NOT_FOUND_STATUS_CODES;
    private String headersPrefix;
    
    private ContentType[] contentTypes = DEFAULT_CONTENT_TYPES;
    private String[] extensions = DEFAULT_EXTENSIONS;
    
    private final GenericDocumentFetcher genericFetcher = 
            new GenericDocumentFetcher();
    
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
    public String getScreenshotDir() {
        return screenshotDir;
    }
    public void setScreenshotDir(String screenshotDir) {
        this.screenshotDir = screenshotDir;
    }
    public String getScreenshotDimensions() {
        return screenshotDimensions;
    }
    public void setScreenshotDimensions(String screenshotDimensions) {
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
    public ContentType[] getContentTypes() {
        return contentTypes;
    }
    public void setContentTypes(ContentType... contentTypes) {
        this.contentTypes = contentTypes;
    }
	public String[] getExtensions() {
        return extensions;
    }
    public void setExtensions(String... extensions) {
        this.extensions = extensions;
    }
    
    @Override
	public HttpFetchResponse fetchDocument(
	        HttpClient httpClient, HttpDocument doc) {

        validate();
        
        if (!isHTMLByExtension(doc.getReference()) && !isHTMLByContentType(doc)) {
            return genericFetcher.fetchDocument(httpClient, doc);
        }

        String fileId = Long.toString(TimeIdGenerator.next());
        
        String url = doc.getReference();
	    File phantomTempdir = doc.getContent().getCacheDirectory();
	    File phantomScriptFile = new File(scriptPath);
	    
        //TODO make configurable?
        File phantomCookiesFile = new File(phantomTempdir, "cookies.txt");
        String phantomScreenshotFile = "";
        if (StringUtils.isNotBlank(screenshotDir)) {
            try {
                phantomScreenshotFile = new File(FileUtil.createDateTimeDirs(
                        new File(screenshotDir)), 
                        fileId + ".png").getAbsolutePath();
                
                // Make key configurable?
                doc.getMetadata().addString(
                        "phantomjs.screenshotfile", phantomScreenshotFile);
            } catch (IOException e) {
                throw new CollectorException("Could not create screenshot "
                        + "directory." + e);
            }
        }
        boolean loadImages = StringUtils.isNotBlank(phantomScreenshotFile);
        
        // outFile is automatically deleted by framework when done with it.
        File outFile = new File(phantomTempdir, fileId);
        String protocol = "http";
        if (url.startsWith("https")) {
            protocol = "https";
        }

	    // Build command
	    List<String> cmdArgs = new ArrayList<>();
	    cmdArgs.add(exePath);
        cmdArgs.add("--ssl-protocol=any");
        if (LOG.isDebugEnabled()) {
            cmdArgs.add("--debug=true");
        }
        cmdArgs.add("--ignore-ssl-errors=true");
        cmdArgs.add("--web-security=false");
        cmdArgs.add("--cookies-file=" 
                + argQuote(phantomCookiesFile.getAbsolutePath()));
        cmdArgs.add("--load-images=" + loadImages);
        // Configure for HttpClient proxy if used.
	    if (HttpClientProxy.isStarted()) {
	        cmdArgs.add("--proxy=" + HttpClientProxy.getProxyHost());
	        
	        cmdArgs.add("--proxy-auth=bindId:" 
	                + HttpClientProxy.getId(httpClient));
            url = url.replaceFirst("^https", "http");
	    }
	    if (ArrayUtils.isNotEmpty(options)) {
	        cmdArgs.addAll(Arrays.asList(options));
	    }
	    cmdArgs.add(argQuote(phantomScriptFile.getAbsolutePath()));
	    cmdArgs.add(argQuote(url));
	    cmdArgs.add(argQuote(outFile.getAbsolutePath()));
	    cmdArgs.add(Integer.toString(renderWaitTime));
        if (HttpClientProxy.isStarted()) {
            cmdArgs.add(Integer.toString(HttpClientProxy.getId(httpClient)));
        } else {
            cmdArgs.add(Integer.toString(-1));
        }
        cmdArgs.add(protocol);
	    cmdArgs.add(argQuote(phantomScreenshotFile));
        cmdArgs.add(argQuote(screenshotDimensions));
        cmdArgs.add(Float.toString(screenshotZoomFactor));
	    
	    SystemCommand cmd = new SystemCommand(
	            cmdArgs.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
	    LOG.debug("Command: " + cmd);
	    CmdOutputGrabber output = new CmdOutputGrabber(
	            cmd, doc.getMetadata(), getHeadersPrefix());
	    cmd.addErrorListener(output);
	    cmd.addOutputListener(output);
	    
	    int exit = 0;
	    try {
            exit = cmd.execute();

            int statusCode = output.getStatusCode();
            String reason = output.getStatusText();

            // set Content-Type HTTP metadata obtained from CONTENTTYPE output
            // if not obtained via regular headers
            if (!doc.getMetadata().containsKey(HttpMetadata.HTTP_CONTENT_TYPE)
                    && StringUtils.isNotBlank(output.getContentType())) {
                doc.getMetadata().setString(HttpMetadata.HTTP_CONTENT_TYPE, 
                        output.getContentType());
            }

            if (!isHTMLByContentType(doc)) {
                return genericFetcher.fetchDocument(httpClient, doc);
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
            
            // VALID response
            if (exit == 0 
                    && ArrayUtils.contains(validStatusCodes, statusCode)) {
                //--- Fetch body
                doc.setContent(doc.getContent().newInputStream(outFile));
                //read a copy to force caching
                IOUtils.copy(doc.getContent(), new NullOutputStream());
                return new HttpFetchResponse(
                        HttpCrawlState.NEW, statusCode, reason);
            }
            
            // INVALID response
            if (LOG.isTraceEnabled()) {
                LOG.trace("Rejected response content: "
                        + FileUtils.readFileToString(
                                outFile, CharEncoding.UTF_8));
            }
            if (ArrayUtils.contains(notFoundStatusCodes, statusCode)) {
                return new HttpFetchResponse(
                        HttpCrawlState.NOT_FOUND, statusCode, reason);
            }
            if (exit != 0) {
                return new HttpFetchResponse(
                        CrawlState.BAD_STATUS, exit, 
                        "PhantomJS execution failed with exit code " + exit);
            }
            LOG.debug("Unsupported HTTP Response: " + reason);
            return new HttpFetchResponse(
                    CrawlState.BAD_STATUS, statusCode, reason);
        } catch (InterruptedException | IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.error("Cannot fetch document: " + doc.getReference()
                        + " (" + e.getMessage() + ")", e);
            } else {
                LOG.error("Cannot fetch document: " + doc.getReference()
                        + " (" + e.getMessage() + ")");
            }
            throw new CollectorException(e);
        } finally {
            // file is deleted by the framework when done with it.
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
        if (StringUtils.isNotBlank(screenshotDir)) {
            File dir = new File(screenshotDir);
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
	    return "\"" + Objects.toString(arg, "") + "\"";
	}
    
    private boolean isHTMLByExtension(String url) {
        if (ArrayUtils.isEmpty(extensions)) {
            return true;
        }
        String urlExt = url.replaceFirst(".*?://.*?/.*\\.(.+?)$", "$1");
        if (!url.equals(urlExt)) {
            for (String htmlExt : extensions) {
                if (StringUtils.removeStart(htmlExt, ".").equalsIgnoreCase(urlExt)) {
                    return true;
                }
            }
        }
        return false;
    }
    private boolean isHTMLByContentType(HttpDocument doc) {
        if (doc.getContentType() != null) {
            return ArrayUtils.contains(contentTypes, doc.getContentType());
        }
        String ct = doc.getMetadata().getString(Objects.toString(headersPrefix, "")
                + HttpMetadata.HTTP_CONTENT_TYPE);
        if (StringUtils.isNotBlank(ct)) {
            ct = ct.replaceFirst("(.*);.*", "$1").trim();
        }
        if (StringUtils.isNotBlank(ct)) {
            return ArrayUtils.contains(contentTypes, ContentType.valueOf(ct));
        }
        // there are no content-type, consider it HTML
        return true;
    }    
    

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = XMLConfigurationUtil.newXMLConfiguration(in);
        setExePath(xml.getString("exePath", getExePath()));
        setScriptPath(xml.getString("scriptPath", getScriptPath()));
        setRenderWaitTime(xml.getInt("renderWaitTime", getRenderWaitTime()));
        setScreenshotDir(xml.getString("screenshotDir", getScreenshotDir()));
        setScreenshotDimensions(xml.getString(
                "screenshotDimensions", getScreenshotDimensions()));
        setScreenshotZoomFactor(xml.getFloat(
                "screenshotZoomFactor", getScreenshotZoomFactor()));
        setValidStatusCodes(XMLConfigurationUtil.getCSVIntArray(
                xml, "validStatusCodes", getValidStatusCodes()));
        setNotFoundStatusCodes(XMLConfigurationUtil.getCSVIntArray(
                xml, "notFoundStatusCodes", getNotFoundStatusCodes()));
        setHeadersPrefix(xml.getString("headersPrefix", getHeadersPrefix()));
        
        String[] opts = xml.getStringArray("options.opt");
        if (ArrayUtils.isNotEmpty(opts)) {
            setOptions(opts);
        }
        ContentType[] cts = ContentType.valuesOf(StringUtils.split(
                StringUtils.trimToNull(xml.getString("contentTypes")), ", "));
        if (!ArrayUtils.isEmpty(cts)) {
            setContentTypes(cts);
        }
        String[] exts = StringUtils.split(
                StringUtils.trimToNull(xml.getString("extensions")), ", ");
        if (!ArrayUtils.isEmpty(exts)) {
            setExtensions(exts);
        }
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);         
            writer.writeStartElement("metadataFetcher");
            writer.writeAttribute("class", getClass().getCanonicalName());

            writer.writeElementString("exePath", exePath);
            writer.writeElementString("scriptPath", scriptPath);
            writer.writeElementInteger("renderWaitTime", renderWaitTime);
            writer.writeElementString("screenshotDir", screenshotDir);
            writer.writeElementString(
                    "screenshotDimensions", screenshotDimensions);
            writer.writeElementFloat(
                    "screenshotZoomFactor", screenshotZoomFactor);
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
            if (!ArrayUtils.isEmpty(getContentTypes())) {
                writer.writeElementString(
                        "contentTypes", StringUtils.join(getContentTypes(), ','));
            }
            if (!ArrayUtils.isEmpty(getExtensions())) {
                writer.writeElementString(
                        "extensions", StringUtils.join(getExtensions(), ','));
            }
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
    
    //Metadata is expected to be outputed, starting with HEADER: on each line
    private static class CmdOutputGrabber implements IStreamListener {
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
        public void lineStreamed(String type, String line) {
            if (ExecUtils.STDERR.equalsIgnoreCase(type)) {
                error.write("\n  " + line);
            } else if (line.startsWith("HEADER:")) {
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