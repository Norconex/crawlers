/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.web.sitemap.impl;

import static org.apache.commons.lang3.StringUtils.substringBeforeLast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.http.HttpStatus;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.core.crawler.CrawlerLifeCycleListener;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.sitemap.SitemapResolutionContext;
import com.norconex.crawler.web.sitemap.SitemapResolver;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Generic implementation of {@link SitemapResolver} that loads and parses
 * <a href="http://www.sitemaps.org/protocol.html">Sitemaps</a>.
 * </p>
 * <p>
 * Sitemaps are only resolved if they have not been
 * resolved already for the same web site domain (the protocol, host name and
 * port).
 * </p>
 *
 * <h3>Additional Sitemap locations</h3>
 * <p>
 * In addition to any Sitemap URLs configured as "start URLs" or
 * found in "robots.txt" files, this Sitemap resolver will also check
 * these two frequent locations:
 * <code>/sitemap.xml</code> and <code>/sitemap_index.xml</code>.
 * You can define you own custom locations instead thanks to
 * {@link #setSitemapPaths(List)}.
 * </p>
 *
 * <h3>Disable Sitemap processing</h3>
 * <p>
 * To disable custom location detection, you can set
 * {@link #setSitemapPaths(List)} to <code>null</code>.  To disable Sitemaps
 * defined in "robots.txt", you can disable "robots.txt" support
 * on your crawler configuration.
 * To disable Sitemap support entirely, you can set
 * {@link WebCrawlerConfig#setSitemapResolver(SitemapResolver)} to
 * <code>null</code>.  Keep in mind doing so will prevent you to define
 * Sitemap URLs as part of your crawler start URLs.
 * </p>
 *
 * <h3>Other consideration</h3>
 * <p>
 * The Sitemap specification dictates that a sitemap.xml file defined
 * in a sub-directory applies only to URLs found in that sub-directory and
 * its children. This behavior is respected by default.  Setting lenient
 * to <code>true</code> no longer honors this restriction.
 * </p>
 * <p>
 * If you know upfront of the existence of one or several Sitemaps, you can
 * safely disable Sitemap detection and define them as part of your start URLs
 * instead. If you only want to rely on Sitemaps to crawl web sites,
 * you can set the crawler <code>maxDepth</code> to zero (0) so that links
 * on crawled Sitemap pages are now followed.
 * </p>
 * <p>
 * Sitemaps specified as "start URLs" will be the only ones resolved for the
 * web site domain they represent. That is, custom Sitemap paths or
 * Sitemaps defined in robots.txt for that domain will be ignored.
 * </p>
 * <p>
 * Sitemaps are first stored in a local temporary file before
 * being parsed. A directory relative to the crawler work directory
 * will be created by default. To specify a custom directory, you can use
 * {@link #setTempDir(Path)}.
 * </p>
 *
 * {@nx.xml.usage
 * <sitemapResolver
 *   class="com.norconex.crawler.web.sitemap.impl.GenericSitemapResolver"
 *   lenient=[false|true]
 * >
 *   <paths>
 *     <!-- multiple path tags allowed -->
 *     <path>(Sitemap URL paths relative to web site domain)</path>
 *   </paths>
 *   <tempDir>
 *     (path to where to temporarily save Sitemaps before parsing them)
 *   </tempDir>
 * </sitemapResolver>
 * }
 *
 * {@nx.xml.example
 * <sitemapResolver class="GenericSitemapResolver">
 *   <paths>
 *     <path>/sitemap.xml</path>
 *     <path>/sitemap_index.xml</path>
 *     <path>/some/sitemap/path.xml</path>
 *   </paths>
 * </sitemapResolver>
 * }
 * <p>
 * The above example adds <code>/some/sitemap/path.xml</code> to the default
 * paths being checked for Sitemaps.
 * </p>
 * <p>
 * To disable Sitemap resolution:
 * </p>
 * {@nx.xml
 * <sitemapResolver />
 * }
 *
 * @since 3.0.0
 * @see SitemapResolver
 */
@Data
@Slf4j
public class GenericSitemapResolver
        extends CrawlerLifeCycleListener
        implements SitemapResolver, XMLConfigurable {

    //TODO eventually check sitemap last modified date and reprocess if
    // changed (or request to have it only if it changed).

    public static final List<String> DEFAULT_SITEMAP_PATHS =
            Collections.unmodifiableList(Arrays.asList(
                    "/sitemap.xml", "/sitemap_index.xml"));

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DataStore<Boolean> resolvedURLRoots;
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final Set<String> activeURLRoots =
            Collections.synchronizedSet(new HashSet<String>());
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private boolean stopping;
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CachedStreamFactory streamFactory;

    // Configurable:
    /** The directory where temporary Sitemap files are written. */
    @Setter(value = AccessLevel.NONE)
    private Path tempDir;
    private boolean lenient;
    private final List<String> sitemapPaths =
            new ArrayList<>(DEFAULT_SITEMAP_PATHS);

    //--- Accessors ------------------------------------------------------------

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
    public void setSitemapPaths(List<String> sitemapPaths) {
        CollectionUtil.setAll(this.sitemapPaths, sitemapPaths);
    }

    //--- Crawler events -------------------------------------------------------

    @Override
    protected void onCrawlerCleanBegin(CrawlerEvent event) {
        deleteTemp();
    }
    @Override
    protected void onCrawlerRunBegin(CrawlerEvent event) {
        streamFactory = event.getSource().getStreamFactory();
        tempDir = event.getSource().getTempDir().resolve("sitemaps");
        try {
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new CrawlerException(
                    "Could not create sitemap temporary directory: "
                            + tempDir, e);
        }
        resolvedURLRoots = Optional.ofNullable(resolvedURLRoots).orElseGet(
                () -> event.getSource().getDataStoreEngine().openStore(
                        "generic-sitemap", Boolean.class));
    }
    @Override
    protected void onCrawlerStopBegin(CrawlerEvent event) {
        stopping = true;
    }
    @Override
    protected void onCrawlerShutdown(CrawlerEvent event) {
        Optional.ofNullable(resolvedURLRoots).ifPresent(c -> {
            c.clear();
            c.close();
        });
        deleteTemp();
        resolvedURLRoots = null;
    }
    private void deleteTemp() {
        Optional.ofNullable(tempDir).ifPresent(dir -> {
            try {
                FileUtil.delete(dir.toFile());
            } catch (IOException e) {
                throw new CrawlerException("Could not delete sitemap "
                        + "temporary directory: " + dir, e);
            }
        });
    }

    //--- Sitemap resolution ---------------------------------------------------

    @Override
    public void resolveSitemaps(SitemapResolutionContext ctx) {

        if (isResolutionRequired(ctx.getUrlRoot())) {
            final Set<String> resolvedLocations = new HashSet<>();
            Set<String> uniqueLocations = null;
            if (ctx.isStartURLs()) {
                uniqueLocations = new HashSet<>(ctx.getSitemapLocations());
            } else {
                uniqueLocations = combineLocations(
                        ctx.getSitemapLocations(), ctx.getUrlRoot());
            }
            LOG.debug("Sitemap locations: {}", uniqueLocations);
            for (String location : uniqueLocations) {
                resolveSitemap(location, ctx.getFetcher(),
                        ctx.getUrlConsumer(), resolvedLocations);
                if (stopping) {
                    break;
                }
            }
            //TODO the boolean serves no purpose now, but eventually,
            // use a date to detect if the sitemap has changed.
            resolvedURLRoots.save(ctx.getUrlRoot(), Boolean.TRUE);
            activeURLRoots.remove(ctx.getUrlRoot());
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

    private void resolveSitemap(
            String location,
            HttpFetcher fetcher,
            Consumer<WebDocRecord> sitemapURLConsumer,
            Set<String> resolvedLocations) {

        if (resolvedLocations.contains(location)) {
            return;
        }

        if (stopping) {
            LOG.debug("Skipping resolution of sitemap "
                    + "location (stop requested): {}", location);
            return;
        }

        final var doc = new MutableObject<CrawlDoc>();
        try {
            LOG.info("Resolving sitemap: {}", location);
            // Execute the method.
            var response = httpGet(location, fetcher, doc);
            var statusCode = response.getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                InputStream is = doc.getValue().getInputStream();
                Optional<String> contentType = Optional
                    .ofNullable(doc.getValue().getDocRecord())
                    .map(CrawlDocRecord::getContentType)
                    .map(ContentType::toString);
                if (contentType.isPresent() && (
                        contentType.get().endsWith("gzip")
                            || location.endsWith(".gz"))) {
                    is = new GZIPInputStream(is);
                }
                var sitemapFile = inputStreamToTempFile(is);
                is.close();
                parseSitemap(sitemapFile, fetcher, sitemapURLConsumer,
                        resolvedLocations, location);
                LOG.info("         Resolved: {}", location);
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                LOG.debug("Sitemap not found : {}", location);
            } else {
                LOG.error("""
                    Could not obtain sitemap: {}.\s\
                    Expected status code {},\s\
                    but got {}.""",
                        location, HttpStatus.SC_OK, statusCode);
            }
        } catch (XMLStreamException e) {
                LOG.error("""
                    Cannot fetch sitemap: {} -- Likely an invalid\s\
                    sitemap XML format causing\s\
                    a parsing error (actual error:\s\
                    {}).""", location, e.getMessage());
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
    private HttpFetchResponse httpGet(
            String location,
            HttpFetcher fetcher,
            MutableObject<CrawlDoc> doc)
                    throws IOException {
        return httpGet(location, fetcher, doc, 0);
    }
    private HttpFetchResponse httpGet(
            String location,
            HttpFetcher fetcher,
            MutableObject<CrawlDoc> doc,
            int loop) throws IOException {

        doc.setValue(new CrawlDoc(new WebDocRecord(location),
                streamFactory.newInputStream()));
        var response = fetcher.fetch(
                new HttpFetchRequest(doc.getValue(), HttpMethod.GET));

        var redirectURL = response.getRedirectTarget();
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
        var tempFile = File.createTempFile(
                "sitemap-", ".xml", tempDir.toFile());
        LOG.debug("Temporarily saving sitemap at: {}",
                tempFile.getAbsolutePath());
        FileUtils.copyInputStreamToFile(is, tempFile);
        return tempFile;
    }

    private void parseSitemap(File sitemapFile, HttpFetcher fetcher,
            Consumer<WebDocRecord> sitemapURLConsumer,
            Set<String> resolvedLocations,
            String location) throws XMLStreamException, IOException {
        try (var fis = new FileInputStream(sitemapFile)) {
            parseSitemap(fis, fetcher,
                    sitemapURLConsumer, resolvedLocations, location);
        }
        FileUtil.delete(sitemapFile);
    }

    void parseSitemap(InputStream is, HttpFetcher fetcher,
            Consumer<WebDocRecord> sitemapURLConsumer,
            Set<String> resolvedLocations,
            String sitemapLocation) throws XMLStreamException, IOException {

        var sitemapLocationDir = substringBeforeLast(sitemapLocation, "/");
        XML.stream(is)
            .takeWhile(c -> {
                if (stopping) {
                    LOG.debug("Sitemap not entirely parsed due to "
                            + "crawler being stopped.");
                    return false;
                }
                return true;
            })
            .forEachOrdered(c -> {
                if ("sitemap".equalsIgnoreCase(c.getLocalName())) {
                    //TODO handle lastmod to speed up re-crawling even further?
                    var url = c.readAsXML().getString("loc");
                    if (StringUtils.isNotBlank(url)) {
                        resolveSitemap(url, fetcher,
                                sitemapURLConsumer, resolvedLocations);
                    }
                } else if ("url".equalsIgnoreCase(c.getLocalName())) {
                    var doc = toDocRecord(c.readAsXML(), sitemapLocationDir);
                    if (doc != null) {
                        sitemapURLConsumer.accept(doc);
                    }
                }
            });
    }

    private WebDocRecord toDocRecord(XML xml, String sitemapLocationDir) {
        var url = xml.getString("loc");

        // Is URL valid?
        if (StringUtils.isBlank(url)
                || (!lenient && !url.startsWith(sitemapLocationDir))) {
            LOG.debug("Sitemap URL invalid for location directory."
                    + " URL: {}  Location directory: {}",
                    url, sitemapLocationDir);
            return null;
        }

        var doc = new WebDocRecord(url);
        doc.setSitemapLastMod(toDateTime(xml.getString("lastmod")));
        doc.setSitemapChangeFreq(xml.getString("changefreq"));
        var priority = xml.getString("priority");
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
            var safePath = path;
            safePath = StringUtils.prependIfMissing(safePath, "/");
            uniqueLocations.add(urlRoot + safePath);
        }
        return uniqueLocations;
    }

    //--- XML Load/Save --------------------------------------------------------

    @Override
    public void loadFromXML(XML xml) {
        xml.checkDeprecated("path", "paths/path", true);

        setLenient(xml.getBoolean("@lenient", lenient));
        setSitemapPaths(xml.getStringList("paths/path", getSitemapPaths()));
    }

    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("lenient", lenient);
        xml.addElement("paths").addElementList("path", getSitemapPaths());
    }
}
