/* Copyright 2019-2020 Norconex Inc.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.crawler.CrawlerLifeCycleListener;
import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.store.IDataStore;
import com.norconex.collector.core.store.SimpleValue;
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
 * being parsed. The <code>tempDir</code> constructor argument is used as the
 * location where to store those files. When <code>null</code>, the system
 * temporary directory is used, as returned by
 * {@link FileUtils#getTempDirectoryPath()}.
 * </p>
 * @author Pascal Essiembre
 * @since 3.0.0 (merged fro StandardSitemapResolver*)
 */
public class GenericSitemapResolver
        extends CrawlerLifeCycleListener
        implements ISitemapResolver, IXMLConfigurable {

    private static final Logger LOG = LoggerFactory.getLogger(
            GenericSitemapResolver.class);

    public static final List<String> DEFAULT_SITEMAP_PATHS =
            Collections.unmodifiableList(Arrays.asList(
                    "/sitemap.xml", "/sitemap_index.xml"));

    private Path tempDir;
    private IDataStore<SimpleValue> resolvedURLRoots;

    private final Set<String> activeURLRoots =
            Collections.synchronizedSet(new HashSet<String>());

    private boolean lenient;
    private boolean stopped;
    private final List<String> sitemapPaths =
            new ArrayList<>(DEFAULT_SITEMAP_PATHS);

    @Override
    protected void onCrawlerRunBegin(CrawlerEvent<Crawler> event) {
        tempDir = Optional.ofNullable(tempDir).orElseGet(
                () -> event.getSource().getTempDir());
        resolvedURLRoots = Optional.ofNullable(resolvedURLRoots).orElseGet(
                () -> event.getSource().getDataStoreEngine().openStore(
                        "generic-sitemap", SimpleValue.class));
    }
    @Override
    protected void onCrawlerStopBegin(CrawlerEvent<Crawler> event) {
        stopped = true;
    }
    @Override
    protected void onCrawlerEvent(CrawlerEvent<Crawler> event) {
        if (event.is(CrawlerEvent.CRAWLER_RUN_END,
                CrawlerEvent.CRAWLER_STOP_END)) {
            Optional.ofNullable(resolvedURLRoots).ifPresent((c) -> {
                c.clear();
                c.close();
            });
            deleteTemp();
        }
    }
    @Override
    protected void onCrawlerCleanBegin(CrawlerEvent<Crawler> event) {
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
                resolveLocation(location, fetcher,
                        sitemapURLConsumer, resolvedLocations);
            }
            resolvedURLRoots.save(new SimpleValue(urlRoot));
//            sitemapStore.markResolved(urlRoot);
            activeURLRoots.remove(urlRoot);
        }
    }

    private synchronized boolean isResolutionRequired(String urlRoot) {
        if (activeURLRoots.contains(urlRoot)
                || resolvedURLRoots.existsById(urlRoot)) {
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

    public IDataStore<SimpleValue> getDataStore() {
        return resolvedURLRoots;
    }
    public void setDataStore(IDataStore<SimpleValue> dataStore) {
        this.resolvedURLRoots = dataStore;
    }

//    @Override
//    public void stop() {
//        this.stopped = true;
////        resolvedURLRoots.close();
//    }

    private void resolveLocation(String location, HttpFetchClient fetcher,
            Consumer<HttpDocInfo> sitemapURLConsumer,
            Set<String> resolvedLocations) {

        if (resolvedLocations.contains(location)) {
            return;
        }

        if (stopped) {
            LOG.debug("Skipping resolution of sitemap "
                    + "location (stop requested): {}", location);
            return;
        }

        CrawlDoc doc = null;
        try {
            // Execute the method.
            doc = new CrawlDoc(
                    location, fetcher.getStreamFactory().newInputStream());
            IHttpFetchResponse response = fetcher.fetch(
                    doc, HttpMethod.GET);
            int statusCode = response.getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                LOG.info("Resolving sitemap: {}", location);
                InputStream is = doc.getInputStream();
                String contentType =
                        doc.getMetadata().getString("Content-Type");
                if ("application/x-gzip".equals(contentType)
                        || "application/gzip".equals(contentType)) {
                    is = new GZIPInputStream(is);
                }
                File sitemapFile = inputStreamToTempFile(is);
                is.close();
                parseLocation(sitemapFile, fetcher, sitemapURLConsumer,
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
            if (doc != null) {
                try {
                    doc.dispose();
                } catch (IOException e) {
                    LOG.error("Could not dispose of sitemap file for: {}",
                            location, e);
                }
            }
        }
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


    private void parseLocation(File sitemapFile, HttpFetchClient fetcher,
            Consumer<HttpDocInfo> sitemapURLConsumer,
            Set<String> resolvedLocations,
            String location) throws XMLStreamException, IOException {

        try (FileInputStream fis = new FileInputStream(sitemapFile)) {
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            inputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
            XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(fis);
            ParseState parseState = new ParseState();

            String locationDir = StringUtils.substringBeforeLast(location, "/");
            int event = xmlReader.getEventType();
            while(true){
                if (stopped) {
                    LOG.debug("Sitemap not entirely parsed due to "
                            + "crawler being stopped.");
                    break;
                }
                switch(event) {
                case XMLStreamConstants.START_ELEMENT:
                    String tag = xmlReader.getLocalName();
                    parseStartElement(parseState, tag);
                    break;
                case XMLStreamConstants.CHARACTERS:
                    String value = xmlReader.getText();
                    if (parseState.sitemapIndex && parseState.loc) {
                        resolveLocation(value, fetcher,
                                sitemapURLConsumer, resolvedLocations);
                        parseState.loc = false;
                    } else if (parseState.baseURL != null) {
                        parseCharacters(parseState, value);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    tag = xmlReader.getLocalName();
                    parseEndElement(
                            sitemapURLConsumer, parseState, locationDir, tag);
                    break;
                }
                if (!xmlReader.hasNext()) {
                    break;
                }
                event = xmlReader.next();
            }
        }
        FileUtil.delete(sitemapFile);
    }

    private void parseEndElement(
            Consumer<HttpDocInfo> sitemapURLConsumer,
            ParseState parseState, String locationDir, String tag) {
        if ("sitemap".equalsIgnoreCase(tag)) {
            parseState.sitemapIndex = false;
        } else if("url".equalsIgnoreCase(tag)
                && parseState.baseURL.getReference() != null){
            if (isRelaxed(parseState, locationDir)) {
                sitemapURLConsumer.accept(parseState.baseURL);
            }
            LOG.debug("Sitemap URL invalid for location directory."
                    + " URL: {}  Location directory: {}",
                    parseState.baseURL.getReference(), locationDir);
            parseState.baseURL = null;
        }
    }

    private boolean isRelaxed(ParseState parseState, String locationDir) {
        return lenient
                || parseState.baseURL.getReference().startsWith(locationDir);
    }

    private void parseCharacters(ParseState parseState, String value) {
        if (parseState.loc) {
            parseState.baseURL.setReference(value);
            parseState.loc = false;
        } else if (parseState.lastmod) {
            try {
                parseState.baseURL.setSitemapLastMod(
                        ZonedDateTime.parse(value));
            } catch (Exception e) {
                LOG.info("Invalid sitemap date: {}", value);
            }
            parseState.lastmod = false;
        } else if (parseState.changefreq) {
            parseState.baseURL.setSitemapChangeFreq(value);
            parseState.changefreq = false;
        } else if (parseState.priority) {
            try {
                parseState.baseURL.setSitemapPriority(
                        Float.parseFloat(value));
            } catch (NumberFormatException e) {
                LOG.info("Invalid sitemap priority: {}", value);
            }
            parseState.priority = false;
        }
    }

    private void parseStartElement(ParseState parseState, String tag) {
        if("sitemap".equalsIgnoreCase(tag)) {
            parseState.sitemapIndex = true;
        } else if("url".equalsIgnoreCase(tag)){
            parseState.baseURL = new HttpDocInfo("", 0);
        } else if("loc".equalsIgnoreCase(tag)){
            parseState.loc = true;
        } else if("lastmod".equalsIgnoreCase(tag)){
            parseState.lastmod = true;
        } else if("changefreq".equalsIgnoreCase(tag)){
            parseState.changefreq = true;
        } else if("priority".equalsIgnoreCase(tag)){
            parseState.priority = true;
        }
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

    private static class ParseState {
        private HttpDocInfo baseURL = null;
        private boolean sitemapIndex = false;
        private boolean loc = false;
        private boolean lastmod = false;
        private boolean changefreq = false;
        private boolean priority = false;
    }
}
