/* Copyright 2019-2021 Norconex Inc.
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

import static org.apache.commons.lang3.StringUtils.substringBeforeLast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.crawler.CrawlerLifeCycleListener;
import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.store.IDataStore;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLCursor;

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
 * The Sitemap specification dictates that a sitemap.xml file defined
 * in a sub-directory applies only to URLs found in that sub-directory and
 * its children. This behavior is respected by default.  Setting lenient
 * to <code>true</code> no longer honors this restriction.
 * </p>
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
 * being parsed. A directory relative to the crawler work directory
 * will be created by default. To specify a custom directory, you can use
 * {@link #setTempDir(Path)}.
 * </p>
 * @author Pascal Essiembre
 * @since 3.0.0 (merged fro StandardSitemapResolver*)
 */
public class GenericSitemapResolver
        extends CrawlerLifeCycleListener
        implements ISitemapResolver, IXMLConfigurable {

    private static final Logger LOG = LoggerFactory.getLogger(
            GenericSitemapResolver.class);

    //TODO Follow redirects and/or print redirect target

    public static final List<String> DEFAULT_SITEMAP_PATHS =
            Collections.unmodifiableList(Arrays.asList(
                    "/sitemap.xml", "/sitemap_index.xml"));

    private Path tempDir;

    //TODO eventually check sitemap last modified date and reprocess if
    // changed (or request to have it only if it changed).

    private IDataStore<Boolean> resolvedURLRoots;

    private final Set<String> activeURLRoots =
            Collections.synchronizedSet(new HashSet<String>());

    private boolean lenient;
    private boolean stopping;
    private final List<String> sitemapPaths =
            new ArrayList<>(DEFAULT_SITEMAP_PATHS);

    @Override
    protected void onCrawlerRunBegin(CrawlerEvent event) {
        tempDir = Optional.ofNullable(tempDir).orElseGet(
                () -> event.getSource().getTempDir());
        resolvedURLRoots = Optional.ofNullable(resolvedURLRoots).orElseGet(
                () -> event.getSource().getDataStoreEngine().openStore(
                        "generic-sitemap", Boolean.class));
    }
    @Override
    protected void onCrawlerStopBegin(CrawlerEvent event) {
        stopping = true;
    }
    @Override
    protected void onCrawlerEvent(CrawlerEvent event) {
        if (event.is(CrawlerEvent.CRAWLER_RUN_END,
                CrawlerEvent.CRAWLER_STOP_END)) {
            Optional.ofNullable(resolvedURLRoots).ifPresent(c -> {
                c.clear();
                c.close();
            });
            deleteTemp();
        }
    }
    @Override
    protected void onCrawlerCleanBegin(CrawlerEvent event) {
        deleteTemp();
    }

    private void deleteTemp() {
        Optional.ofNullable(tempDir).ifPresent(dir -> {
            try {
                FileUtil.delete(dir.toFile());
            } catch (IOException e) {
                throw new CollectorException("Could not delete sitemap "
                        + "temporary directory: " + dir, e);
            }
        });
    }

    /**
     * Gets the URL paths, relative to the URL root, from which to try
     * locate and resolve sitemaps. Default paths are
     * "/sitemap.xml" and "/sitemap-index.xml".
     * @return sitemap paths.
     */
    public List<String> getSitemapPaths() {
        return Collections.unmodifiableList(sitemapPaths);
    }
    /**
     * Sets the URL paths, relative to the URL root, from which to try
     * locate and resolve sitemaps.
     * @param sitemapPaths sitemap paths.
     */
    public void setSitemapPaths(String... sitemapPaths) {
        CollectionUtil.setAll(this.sitemapPaths, sitemapPaths);
    }
    /**
     * Sets the URL paths, relative to the URL root, from which to try
     * locate and resolve sitemaps.
     * @param sitemapPaths sitemap paths.
     */
    public void setSitemapPaths(List<String> sitemapPaths) {
        CollectionUtil.setAll(this.sitemapPaths, sitemapPaths);
    }

    @Override
    public void resolveSitemaps(
            HttpFetchClient fetcher, String urlRoot,
            List<String> sitemapLocations,
            Consumer<HttpDocInfo> sitemapURLConsumer,
            boolean startURLs) {

        if (isResolutionRequired(urlRoot)) {
            final Set<String> resolvedLocations = new HashSet<>();
            Set<String> uniqueLocations = null;
            if (startURLs) {
                uniqueLocations = new HashSet<>();
                uniqueLocations.addAll(sitemapLocations);
            } else {
                uniqueLocations = combineLocations(sitemapLocations, urlRoot);
            }
            LOG.debug("Sitemap locations: {}", uniqueLocations);
            for (String location : uniqueLocations) {
                resolveSitemap(location, fetcher,
                        sitemapURLConsumer, resolvedLocations);
                if (stopping) {
                    break;
                }
            }
            //TODO the boolean serves no purpose now, but eventually,
            // use a date to detect if the sitemap has changed.
            resolvedURLRoots.save(urlRoot, Boolean.TRUE);
            activeURLRoots.remove(urlRoot);
        }
    }

    private synchronized boolean isResolutionRequired(String urlRoot) {
        if (activeURLRoots.contains(urlRoot)
                || resolvedURLRoots.exists(urlRoot)) {
            LOG.trace("Sitemap locations were already processed or are "
                    + "being processed for URL root: {}", urlRoot);
            return false;
        }
        activeURLRoots.add(urlRoot);
        return true;
    }

    public boolean isLenient() {
        return lenient;
    }
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    /**
     * Gets the directory where temporary sitemap files are written.
     * @return directory
     */
    public Path getTempDir() {
        return tempDir;
    }
    /**
     * Sets the directory where temporary sitemap files are written.
     * @param tempDir directory
     */
    public void setTempDir(Path tempDir) {
        this.tempDir = tempDir;
    }

    private void resolveSitemap(
            String location,
            HttpFetchClient fetcher,
            Consumer<HttpDocInfo> sitemapURLConsumer,
            Set<String> resolvedLocations) {

        if (resolvedLocations.contains(location)) {
            return;
        }

        if (stopping) {
            LOG.debug("Skipping resolution of sitemap "
                    + "location (stop requested): {}", location);
            return;
        }

        final MutableObject<CrawlDoc> doc = new MutableObject<>();
        try {
            LOG.info("Resolving sitemap: {}", location);
            // Execute the method.
            IHttpFetchResponse response = httpGet(location, fetcher, doc);
            int statusCode = response.getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                InputStream is = doc.getValue().getInputStream();
                String contentType =
                        doc.getValue().getMetadata().getString("Content-Type");
                if (StringUtils.endsWithIgnoreCase(contentType, "gzip")
                        || StringUtils.endsWithIgnoreCase(location, ".gz")) {
                    is = new GZIPInputStream(is);
                }
                File sitemapFile = inputStreamToTempFile(is);
                is.close();
                parseSitemap(sitemapFile, fetcher, sitemapURLConsumer,
                        resolvedLocations, location);
                LOG.info("         Resolved: {}", location);
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                LOG.debug("Sitemap not found : {}", location);
            } else {
                LOG.error("Could not obtain sitemap: {}. "
                        + "Expected status code {}, "
                        + "but got {}.",
                        location, HttpStatus.SC_OK, statusCode);
            }
        } catch (XMLStreamException e) {
                LOG.error("Cannot fetch sitemap: {} -- Likely an invalid "
                        + "sitemap XML format causing "
                        + "a parsing error (actual error: "
                        + "{}).", location, e.getMessage());
        } catch (Exception e) {
            LOG.error("Cannot fetch sitemap: {} ({})",
                    location, e.getMessage(), e);
        } finally {
            resolvedLocations.add(location);
            if (doc.getValue() != null) {
                try {
                    doc.getValue().dispose();
                } catch (IOException e) {
                    LOG.error("Could not dispose of sitemap file for: {}",
                            location, e);
                }
            }
        }
    }

    // Follow redirects
    private IHttpFetchResponse httpGet(
            String location,
            HttpFetchClient fetcher,
            MutableObject<CrawlDoc> doc)
                    throws IOException {
        return httpGet(location, fetcher, doc, 0);
    }
    private IHttpFetchResponse httpGet(
            String location,
            HttpFetchClient fetcher,
            MutableObject<CrawlDoc> doc,
            int loop)
                    throws IOException {
        doc.setValue(new CrawlDoc(new HttpDocInfo(location),
                fetcher.getStreamFactory().newInputStream()));
        IHttpFetchResponse response =
                fetcher.fetch(doc.getValue(), HttpMethod.GET);
        String redirectURL = response.getRedirectTarget();
        if (StringUtils.isNotBlank(redirectURL)
                && !redirectURL.equalsIgnoreCase(location)) {
            if (loop >= 100) {
                LOG.error("Sitemap redirect loop detected. "
                        + "Last redirect: {} --> {}", location, redirectURL);
                return response;
            }
            LOG.info("         Redirect: {} --> {}", location, redirectURL);
            doc.getValue().dispose();
            return httpGet(redirectURL, fetcher, doc, loop + 1);
        }
        return response;
    }


    /*
     * Saving sitemap locally first to prevent connection/socket timeouts
     * as reported in github #150.
     */
    private File inputStreamToTempFile(InputStream is) throws IOException {
        File safeTempDir = getTempDir() == null ? null : getTempDir().toFile();
        if (safeTempDir == null) {
            safeTempDir = FileUtils.getTempDirectory();
        }
        safeTempDir.mkdirs();
        File tempFile = File.createTempFile("sitemap-", ".xml", safeTempDir);
        LOG.debug("Temporarily saving sitemap at: {}",
                tempFile.getAbsolutePath());
        FileUtils.copyInputStreamToFile(is, tempFile);
        return tempFile;
    }


    private void parseSitemap(File sitemapFile, HttpFetchClient fetcher,
            Consumer<HttpDocInfo> sitemapURLConsumer,
            Set<String> resolvedLocations,
            String location) throws XMLStreamException, IOException {
        try (FileInputStream fis = new FileInputStream(sitemapFile)) {
            parseSitemap(fis, fetcher,
                    sitemapURLConsumer, resolvedLocations, location);
        }
        FileUtil.delete(sitemapFile);
    }

    void parseSitemap(InputStream is, HttpFetchClient fetcher,
            Consumer<HttpDocInfo> sitemapURLConsumer,
            Set<String> resolvedLocations,
            String sitemapLocation) throws XMLStreamException, IOException {

        String sitemapLocationDir = substringBeforeLast(sitemapLocation, "/");
        Iterator<XMLCursor> it = XML.iterator(is);
        while (it.hasNext()) {
            XMLCursor c = it.next();

            if (stopping) {
                LOG.debug("Sitemap not entirely parsed due to "
                        + "crawler being stopped.");
                break;
            }

            if ("sitemap".equalsIgnoreCase(c.getLocalName())) {
                //TODO handle lastmod to speed up re-crawling even further?
                String url = c.readAsXML().getString("loc");
                if (StringUtils.isNotBlank(url)) {
                    resolveSitemap(url, fetcher,
                            sitemapURLConsumer, resolvedLocations);
                }
            } else if ("url".equalsIgnoreCase(c.getLocalName())) {
                HttpDocInfo doc = toDocInfo(c.readAsXML(), sitemapLocationDir);
                if (doc != null) {
                    sitemapURLConsumer.accept(doc);
                }
            }
        }
    }

    private HttpDocInfo toDocInfo(XML xml, String sitemapLocationDir) {
        String url = xml.getString("loc");

        // Is URL valid?
        if (StringUtils.isBlank(url)
                || !(lenient || url.startsWith(sitemapLocationDir))) {
            LOG.debug("Sitemap URL invalid for location directory."
                    + " URL: {}  Location directory: {}",
                    url, sitemapLocationDir);
            return null;
        }

        HttpDocInfo doc = new HttpDocInfo(url);
        doc.setSitemapLastMod(toDateTime(xml.getString("lastmod")));
        doc.setSitemapChangeFreq(xml.getString("changefreq"));
        String priority = xml.getString("priority");
        if (StringUtils.isNotBlank(priority)) {
            try {
                doc.setSitemapPriority(Float.parseFloat(priority));
            } catch (NumberFormatException e) {
                LOG.info("Invalid sitemap urlset/url/priority: {}",
                        priority);
            }
        }
        LOG.debug("Sitemap urlset/url: {}", doc.getReference());
        return doc;
    }

    private ZonedDateTime toDateTime(String value) {
        ZonedDateTime zdt = null;
        if (StringUtils.isBlank(value)) {
            return zdt;
        }
        try {
            if (value.contains("T")) {
                // has time
                zdt = ZonedDateTime.parse(value);
            } else {
                // has no time
                zdt = ZonedDateTime.of(LocalDate.parse(value),
                        LocalTime.MIDNIGHT, ZoneOffset.UTC);
            }
        } catch (Exception e) {
            LOG.info("Invalid sitemap date: {}", value);
        }
        return zdt;
    }

    private Set<String> combineLocations(
            List<String> sitemapLocations, String urlRoot) {
        Set<String> uniqueLocations = new HashSet<>();

        // collector-supplied locations (e.g. from robots.txt or startURLs)
        if (!sitemapLocations.isEmpty()) {
            uniqueLocations.addAll(sitemapLocations);
        }

        // derived locations from sitemap paths
        if (sitemapPaths.isEmpty()) {
            LOG.debug("No sitemap paths specified.");
            return uniqueLocations;
        }

        for (String path : sitemapPaths) {
            String safePath = path;
            safePath = StringUtils.prependIfMissing(safePath, "/");
            uniqueLocations.add(urlRoot + safePath);
        }
        return uniqueLocations;
    }

    @Override
    public void loadFromXML(XML xml) {
        setTempDir(xml.getPath("tempDir", tempDir));
        setLenient(xml.getBoolean("@lenient", lenient));

        //TODO make sure null can be set to clear the paths instead of this hack
        List<String> paths = xml.getStringList("path");
        if (paths.size() == 1 && paths.get(0).equals("")) {
            setSitemapPaths((List<String>) null);
        } else if (!paths.isEmpty()) {
            setSitemapPaths(paths);
        }
    }

    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("lenient", lenient);
        xml.addElement("tempDir", getTempDir());
        if (sitemapPaths.isEmpty()) {
            xml.addElement("path", "");
        } else {
            xml.addElementList("path", sitemapPaths);
        }

        //TODO make sure null can be set to clear the paths
        // could be done for list if always wrapping lists in parent tag
        // that when present but empty, means null.
    }


    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other, "stopped");
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, "stopped");
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE)
                .setExcludeFieldNames("stopped").toString();
    }
}
