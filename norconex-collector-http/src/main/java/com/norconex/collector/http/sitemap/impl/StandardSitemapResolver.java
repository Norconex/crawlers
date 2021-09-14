/* Copyright 2010-2021 Norconex Inc.
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
package com.norconex.collector.http.sitemap.impl;

import static java.lang.Float.parseFloat;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.redirect.RedirectStrategyWrapper;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.collector.http.sitemap.SitemapURLAdder;
import com.norconex.commons.lang.file.FileUtil;

/**
 * <p>
 * Implementation of {@link ISitemapResolver} as per sitemap.xml standard
 * defined at <a href="http://www.sitemaps.org/protocol.html">
 * http://www.sitemaps.org/protocol.html</a>.
 * </p>
 * <p>
 * Sitemaps are only resolved if they have not been
 * resolved already for the same URL "root" (the protocol, host and
 * possible port).
 * </p>
 * <p>
 * The Sitemap specifications dictates that a sitemap.xml file defined
 * in a sub-directory applies only to URLs found in that sub-directory and
 * its children. This behavior is respected by default.  Setting lenient
 * to <code>true</code> no longer honors this restriction.
 * <b>Since 2.9.1</b>, setting lenient will also attempt to parse
 * XML values with invalid entities.
 * </p>
 * <h3>Since 2.3.0</h3>
 * <p>
 * Paths relative to URL roots can be specified and an attempt will be made
 * to load and parse any sitemap found at those locations for each root URLs
 * encountered (except for "start URLs" sitemaps, see below). Default paths
 * are <code>/sitemap.xml</code> and <code>/sitemap_index.xml</code>.
 * Setting <code>null</code> or an empty path array on
 * {@link #setSitemapPaths(String...)} will prevent attempts to locate
 * sitemaps and only sitemaps found in robots.txt or defined as start
 * URLs will be considered.
 * </p>
 * <p>
 * Sitemaps can be specified as "start URLs" (defined in
 * {@link HttpCrawlerConfig#getStartSitemapURLs()}). Sitemaps defined
 * that way will be the only ones resolved for the root URL they represent
 * (sitemap paths or sitemaps defined in robots.txt won't apply).
 * </p>
 * <p>
 * Sitemaps are first stored in a local temporary file before
 * being parsed. The <code>tempDir</code> constructor argument is used as the
 * location where to store those files. When <code>null</code>, the system
 * temporary directory is used, as returned by
 * {@link FileUtils#getTempDirectoryPath()}.
 * </p>
 * @author Pascal Essiembre
 */
public class StandardSitemapResolver implements ISitemapResolver {

    private static final Logger LOG = LogManager.getLogger(
            StandardSitemapResolver.class);

    public static final String[] DEFAULT_SITEMAP_PATHS =
            new String[] { "/sitemap.xml", "/sitemap_index.xml" };

    private final SitemapStore sitemapStore;
    private final Set<String> activeURLRoots =
            Collections.synchronizedSet(new HashSet<String>());

    private boolean lenient;
    private boolean stopped;
    private File tempDir;
    private String[] sitemapPaths = DEFAULT_SITEMAP_PATHS;
    private long fromDate;
    private boolean escalateErrors;

    public StandardSitemapResolver(
            File tempDir,
            SitemapStore sitemapStore) {
        super();
        this.tempDir = tempDir;
        this.sitemapStore = sitemapStore;
        this.fromDate = -1;
        this.escalateErrors = false;
    }

    /**
     * Gets the URL paths, relative to the URL root, from which to try
     * locate and resolve sitemaps. Default paths are
     * "/sitemap.xml" and "/sitemap-index.xml".
     * @return sitemap paths.
     * @since 2.3.0
     */
    public String[] getSitemapPaths() {
        return sitemapPaths;
    }
    /**
     * Sets the URL paths, relative to the URL root, from which to try
     * locate and resolve sitemaps.
     * @param sitemapPaths sitemap paths.
     * @since 2.3.0
     */
    public void setSitemapPaths(String... sitemapPaths) {
        this.sitemapPaths = ArrayUtils.clone(sitemapPaths);
    }

    @Override
    public void resolveSitemaps(
            HttpClient httpClient, String urlRoot,
            String[] sitemapLocations, SitemapURLAdder sitemapURLAdder,
            boolean startURLs) {

        if (isResolutionRequired(urlRoot)) {
            Set<String> uniqueLocations = null;
            if (startURLs) {
                uniqueLocations = new HashSet<>();
                uniqueLocations.addAll(Arrays.asList(sitemapLocations));
            } else {
                uniqueLocations = combineLocations(sitemapLocations, urlRoot);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Sitemap locations: " + uniqueLocations);
            }
            for (String location : uniqueLocations) {
                ParseContext ctx =
                        new ParseContext(sitemapURLAdder, httpClient);
                resolveLocation(location, ctx);
            }
            if(!stopped) {
                sitemapStore.markResolved(urlRoot);
            }
            activeURLRoots.remove(urlRoot);
        }
    }

    private synchronized boolean isResolutionRequired(String urlRoot) {
        if (activeURLRoots.contains(urlRoot)
                || sitemapStore.isResolved(urlRoot)) {
            LOG.trace("Sitemap locations were already processed or are "
                    + "being processed for URL root: " + urlRoot);
            return false;
        }
        activeURLRoots.add(urlRoot);
        return true;
    }

    /**
     * Get sitemap locations.
     * @return sitemap locations
     * @deprecated Since 2.3.0, use
     *             {@link HttpCrawlerConfig#getStartSitemapURLs()}
     */
    @Deprecated
    public String[] getSitemapLocations() {
        LOG.warn("Since 2.3.0, calling StandardSitemapResolver"
                + "#getSitemapLocation() has no effect. "
                + "Use HttpCrawlerConfig#getSitemaps() instead.");
        return null;
    }
    /**
     * Set sitemap locations.
     * @param sitemapLocations sitemap locations
     * @deprecated Since 2.3.0, use
     *             {@link HttpCrawlerConfig#setStartSitemapURLs(String[])}
     */
    @Deprecated
    public void setSitemapLocations(String... sitemapLocations) {
        LOG.warn("Since 2.3.0, calling StandardSitemapResolver"
                + "#setSitemapLocation(String...) has no effect. "
                + "Use HttpCrawlerConfig#setSitemaps(String[] ...) instead.");
    }

    public boolean isLenient() {
        return lenient;
    }
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    /**
     * Gets the minimum EPOCH date (in milliseconds) a sitemap entry
     * should have to be considered.
     * @return from date
     * @since 2.9.1
     */
    public long getFromDate() {
        return fromDate;
    }

    /**
     * Sets the minimum EPOCH date (in milliseconds) a sitemap entry
     * should have to be considered.
     * @param fromDate from date
     * @since 2.9.1
     */
    public void setFromDate(long fromDate) {
        this.fromDate = fromDate;
    }

    /**
     * Gets whether errors should be thrown instead of logged.
     * @return <code>true</code> if throwing errors
     * @since 2.9.1
     */
    public boolean isEscalateErrors() {
        return escalateErrors;
    }
    /**
     * Sets whether errors should be thrown instead of logged.
     * @param escalateErrors <code>true</code> if throwing errors
     * @since 2.9.1
     */
    public void setEscalateErrors(boolean escalateErrors) {
        this.escalateErrors = escalateErrors;
    }

    /**
     * Gets the directory where temporary sitemap files are written.
     * @return directory
     * @since 2.3.0
     */
    public File getTempDir() {
        return tempDir;
    }
    /**
     * Sets the directory where temporary sitemap files are written.
     * @param tempDir directory
     * @since 2.3.0
     */
    public void setTempDir(File tempDir) {
        this.tempDir = tempDir;
    }

    @Override
    public void stop() {
        this.stopped = true;
        sitemapStore.close();
    }

    private void resolveLocation(String location, ParseContext ctx) {
        if (ctx.resolvedLocations.contains(location)) {
            return;
        }

        if (stopped) {
            LOG.debug("Skipping resolution of sitemap "
                    + "location (stop requested): " + location);
            return;
        }

        final MutableObject<HttpGet> method = new MutableObject<>();
        try {
            // Execute the method.
            LOG.info("Resolving sitemap: " + location);
            HttpResponse response = httpGet(location, ctx.httpClient, method);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                InputStream is = response.getEntity().getContent();
                Header ctHeader = response.getFirstHeader("Content-Type");
                if((ctHeader != null && ctHeader.getValue().endsWith("gzip"))
                        || location.endsWith(".gz")) {
                    is = new GZIPInputStream(is);
                }
                File sitemapFile = inputStreamToTempFile(is);
                IOUtils.closeQuietly(is);
                parseLocation(location, sitemapFile, ctx);
                LOG.info("         Resolved: " + location);
            } else if (statusCode == HttpStatus.SC_NO_CONTENT) {
                LOG.info("         Resolved: " + location + " but no content.");
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                String msg = "Sitemap not found : " + location;
                if (escalateErrors) {
                    throw new CollectorException(msg);
                } else {
                    LOG.debug(msg);
                }
            } else {
                String msg = "Could not obtain sitemap: " + location
                        + ".  Expected status code " + HttpStatus.SC_OK
                        + ", but got " + statusCode;
                if (escalateErrors) {
                    throw new CollectorException(msg);
                } else {
                    LOG.error(msg);
                }
            }
        } catch (XMLStreamException e) {
            String msg = "Cannot fetch sitemap: " + location
                    + " -- Likely an invalid sitemap XML format causing "
                    + "a parsing error.";
            if (escalateErrors) {
                throw new CollectorException(msg, e);
            } else {
                LOG.error(msg + " (" + (StringUtils.isBlank(e.getMessage())
                        ? e.getClass().getCanonicalName()
                        : e.getMessage()) + ")");
            }
        } catch (Exception e) {
            String msg = "Cannot fetch sitemap: " + location;
            if (escalateErrors) {
                throw new CollectorException(msg, e);
            } else {
                LOG.error(msg + " (" + (StringUtils.isBlank(e.getMessage())
                        ? e.getClass().getCanonicalName()
                        : e.getMessage()) + ")");
            }
        } finally {
            ctx.resolvedLocations.add(location);
            if (method.getValue() != null) {
                method.getValue().releaseConnection();
            }
        }
    }

    // Follow redirects
    private HttpResponse httpGet(String location,
            HttpClient httpClient, MutableObject<HttpGet> method)
                    throws IOException {
        return httpGet(location, httpClient, method, 0);
    }
    private HttpResponse httpGet(String location,
            HttpClient httpClient, MutableObject<HttpGet> method, int loop)
                    throws IOException {

        method.setValue(new HttpGet(location));
        HttpResponse response = httpClient.execute(method.getValue());
        String redirectURL = RedirectStrategyWrapper.getRedirectURL();
        if (StringUtils.isNotBlank(redirectURL)
                && !redirectURL.equalsIgnoreCase(location)) {
            if (loop >= 100) {
                LOG.error("Sitemap redirect loop detected. Last redirect: '"
                        + location + "' --> '" + redirectURL + "'.");
                return response;
            }
            LOG.info("         Redirect: "
                + location + " --> " + redirectURL);
            method.getValue().releaseConnection();
            return httpGet(redirectURL, httpClient, method, loop + 1);
        }
        return response;
    }

    /*
     * Saving sitemap locally first to prevent connection/socket timeouts
     * as reported in github #150.
     */
    private File inputStreamToTempFile(InputStream is) throws IOException {
        File safeTempDir = getTempDir();
        if (safeTempDir == null) {
            safeTempDir = FileUtils.getTempDirectory();
        }
        File tempFile = File.createTempFile("sitemap-", ".xml", safeTempDir);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Temporarily saving sitemap at: "
                    + tempFile.getAbsolutePath());
        }
        FileUtils.copyInputStreamToFile(is, tempFile);
        return tempFile;
    }

    private void parseLocation(
            String location, File sitemapFile, ParseContext ctx)
                    throws XMLStreamException, IOException {
        String locationDir = StringUtils.substringBeforeLast(location, "/");
        try (InputStream is = lenient
                ? new StripInvalidCharInputStream(
                        new FileInputStream(sitemapFile))
                : new FileInputStream(sitemapFile)) {
            parseXml(locationDir, is, ctx);
        }
        FileUtil.delete(sitemapFile);
    }

    void parseXml(String locationDir, InputStream is, ParseContext ctx)
            throws XMLStreamException, IOException {
        XMLInputFactory inputFactory = XMLInputFactory.newInstance();
        inputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
        XMLEventReader reader = inputFactory.createXMLEventReader(is);
        LinkedList<String> path = new LinkedList<>();
        while (reader.hasNext()) {
            if (stopped) {
                LOG.info("Sitemap not entirely parsed due to "
                        + "crawler being stopped.");
                break;
            }
            XMLEvent ev = reader.nextEvent();
            if (ev.isStartElement()) {
                path.addLast(ev.asStartElement()
                        .getName().getLocalPart().toLowerCase());
                parseElement(path, locationDir, reader, ctx);
            } else if (ev.isEndElement()) {
                path.removeLast();
            }
        }
        reader.close();
    }

    private void parseElement(
            LinkedList<String> path,
            String locationDir,
            XMLEventReader r,
            ParseContext ctx)
                    throws XMLStreamException {

        String pathStr = StringUtils.join(path, "/");
        if (!StringUtils.equalsAny(pathStr,
                "sitemapindex/sitemap",
                "urlset/url")) {
            return;
        }

        Map<String, String> map = elementAsMap(path, r);
        if ("sitemapindex/sitemap".equals(pathStr)) {
            resolveLocation(map.get("loc"), ctx);
        } else if ("urlset/url".equals(pathStr)) {
            HttpCrawlData crawlData = new HttpCrawlData();
            crawlData.setReference(map.get("loc"));
            try {
                DateTime dt = DateTime.parse(map.get("lastmod"));
                if (dt != null) {
                    crawlData.setSitemapLastMod(dt.getMillis());
                }
            } catch (Exception e) {
                LOG.info("Invalid sitemap date: " + map.get("lastmod"));
            }
            crawlData.setSitemapChangeFreq(map.get("changefreq"));
            try {
                String priority = map.get("priority");
                if (priority != null) {
                    crawlData.setSitemapPriority(parseFloat(priority));
                }
            } catch (NumberFormatException e) {
                LOG.info("Invalid sitemap priority: " + map.get("priority"));
            }
            if (crawlData.getReference() != null) {
                if (isValidLocation(crawlData, locationDir)) {
                    if(isRecentEnough(crawlData)) {
                        ctx.sitemapURLAdder.add(crawlData);
                    }
                } else if (LOG.isDebugEnabled()) {
                    LOG.debug("Sitemap URL invalid for location directory."
                            + " URL:" + crawlData.getReference()
                            + " Location directory: " + locationDir);
                }
            }
        }
    }

    private boolean isValidLocation(
            HttpCrawlData crawlData, String locationDir) {
        return lenient || crawlData.getReference().startsWith(locationDir);
    }

    private Map<String, String> elementAsMap(
            LinkedList<String> elPath, XMLEventReader r)
                    throws XMLStreamException {
        Map<String, String> map = new HashMap<>();
        LinkedList<String> currPath = new LinkedList<>(elPath);
        String name = null;
        while (!r.peek().isEndElement() || !elPath.equals(currPath) ) {
            XMLEvent ev = r.nextEvent();
            if (ev.isStartElement()) {
                String prefix = StringUtils.trimToEmpty(
                        ev.asStartElement().getName().getPrefix());
                if (prefix.length() > 0) {
                    prefix += ":";
                }
                name = prefix + ev.asStartElement()
                        .getName().getLocalPart().toLowerCase();
                currPath.addLast(name);
            } else if (ev.isEndElement()) {
                name = null;
                currPath.removeLast();
            } else if (ev.isCharacters()) {
                String value = trimToNull(ev.asCharacters().getData());
                if (!StringUtils.isAnyBlank(name, value)) {
                    map.put(name, value);
                }
            }
        }
        return map;
    }

    private boolean isRecentEnough(HttpCrawlData crawlData) {
        Long lastMod = crawlData.getSitemapLastMod();
        if(fromDate > 0 && lastMod != null) {
            return lastMod > fromDate;
        } else {
            return true;
        }
    }

    private Set<String> combineLocations(
            String[] sitemapLocations, String urlRoot) {
        Set<String> uniqueLocations = new HashSet<>();

        // collector-supplied locations (e.g. from robots.txt or startURLs)
        if (ArrayUtils.isNotEmpty(sitemapLocations)) {
            uniqueLocations.addAll(Arrays.asList(sitemapLocations));
        }

        // derived locations from sitemap paths
        String[] paths = getSitemapPaths();
        if (ArrayUtils.isEmpty(paths)) {
            LOG.debug("No sitemap paths specified.");
            return uniqueLocations;
        }

        for (String path : paths) {
            String safePath = path;
            if (!safePath.startsWith("/")) {
                safePath = "/" + safePath;
            }
            uniqueLocations.add(urlRoot + safePath);
        }
        return uniqueLocations;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof StandardSitemapResolver)) {
            return false;
        }
        StandardSitemapResolver castOther = (StandardSitemapResolver) other;
        return new EqualsBuilder()
                .append(lenient, castOther.lenient)
                .append(tempDir, castOther.tempDir)
                .append(sitemapPaths, castOther.sitemapPaths)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(lenient)
                .append(tempDir)
                .append(sitemapPaths)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("lenient", lenient)
                .append("tempDir", tempDir)
                .append("sitemapPaths", sitemapPaths)
                .toString();
    }

    static class ParseContext {
        private final SitemapURLAdder sitemapURLAdder;
        private final HttpClient httpClient;
        private final Set<String> resolvedLocations = new HashSet<>();
        public ParseContext(
                SitemapURLAdder sitemapURLAdder,
                HttpClient httpClient) {
            super();
            this.sitemapURLAdder = sitemapURLAdder;
            this.httpClient = httpClient;
        }
    }
}
