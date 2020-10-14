/* Copyright 2010-2017 Norconex Inc.
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.data.HttpCrawlData;
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
    private long from;
    private boolean escalateErrors;


    public StandardSitemapResolver(
            File tempDir, 
            SitemapStore sitemapStore) {
        super();
        this.tempDir = tempDir;
        this.sitemapStore = sitemapStore;
        from = -1;
        escalateErrors = false;
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
            final Set<String> resolvedLocations = new HashSet<String>();
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
                resolveLocation(location, httpClient, 
                        sitemapURLAdder, resolvedLocations);
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

    public long getFrom() {
        return from;
    }
    public void setFrom(long from) {
        this.from = from;
    }

    public boolean isEscalateErrors() {
        return escalateErrors;
    }
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

    private void resolveLocation(String location, HttpClient httpClient,
            SitemapURLAdder sitemapURLAdder, Set<String> resolvedLocations) {

        if (resolvedLocations.contains(location)) {
            return;
        }

        if (stopped) {
            LOG.debug("Skipping resolution of sitemap "
                    + "location (stop requested): " + location);
            return;
        }
        
        HttpGet method = null;
        try {
            method = new HttpGet(location);
            
            // Execute the method.
            HttpResponse response = httpClient.execute(method);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                LOG.info("Resolving sitemap: " + location);
                InputStream is = response.getEntity().getContent();
                Header header = response.getFirstHeader("Content-Type");
                if(header != null && header.getValue().endsWith("gzip")) {
                    is = new GZIPInputStream(is);
                }
                File sitemapFile = inputStreamToTempFile(is);
                IOUtils.closeQuietly(is);
                parseLocation(sitemapFile, httpClient, sitemapURLAdder, 
                        resolvedLocations, location);
                LOG.info("         Resolved: " + location);
            } else if (statusCode == HttpStatus.SC_NO_CONTENT) {
                LOG.info("         Resolved: " + location + " but no content.");
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                LOG.debug("Sitemap not found : " + location);
                if(escalateErrors) {
                    throw new RuntimeException("Sitemap not found : " + location);
                }
            } else {
                LOG.error("Could not obtain sitemap: " + location
                        + ".  Expected status code " + HttpStatus.SC_OK
                        + ", but got " + statusCode);
                if(escalateErrors) {
                    throw new RuntimeException("Could not obtain sitemap: " + location
                            + ".  Expected status code " + HttpStatus.SC_OK
                            + ", but got " + statusCode);
                }
            }
        } catch (XMLStreamException e) {
                LOG.error("Cannot fetch sitemap: " + location
                        + " -- Likely an invalid sitemap XML format causing "
                        + "a parsing error (actual error: " 
                        + e.getMessage() + ").");
                if(escalateErrors) {
                    throw new RuntimeException(e);
                }
        } catch (Exception e) {
            LOG.error("Cannot fetch sitemap: " + location
                    + " (" + e.getMessage() + ")");
            if(escalateErrors) {
                throw new RuntimeException(e);
            }
        } finally {
            resolvedLocations.add(location);
            if (method != null) {
                method.releaseConnection();
            }
        }  
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
    
    
    private void parseLocation(File sitemapFile, HttpClient httpClient,
            SitemapURLAdder sitemapURLAdder, Set<String> resolvedLocations,
            String location) throws XMLStreamException, IOException {

        try (FileInputStream fis = new FileInputStream(sitemapFile)) {
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            inputFactory.setProperty(XMLInputFactory.IS_COALESCING, true);
            XMLStreamReader xmlReader = inputFactory.createXMLStreamReader(new StripInvalidCharInputStream(fis));
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
//                    if (parseState.sitemapIndex && parseState.loc && passedFrom(parseState)) {
//                        resolveLocation(value, httpClient,
//                                sitemapURLAdder, resolvedLocations);
//                        parseState.loc = false;
//                    } else if (parseState.baseURL != null) {
//                        parseCharacters(parseState, value);
//                    }
                    parseCharacters(parseState, value);
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    tag = xmlReader.getLocalName();
                    parseEndElement(
                            sitemapURLAdder, parseState, locationDir, tag, httpClient, resolvedLocations);
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

    private boolean passedFrom(ParseState parseState) {
        Long lastMod = parseState.baseURL.getSitemapLastMod();
        if(from > 0 && lastMod != null) {
            if(lastMod > from) {
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    private void parseEndElement(SitemapURLAdder sitemapURLAdder,
            ParseState parseState, String locationDir, String tag,
            HttpClient httpClient, Set<String> resolvedLocations) {
        if ("sitemap".equalsIgnoreCase(tag)) {
            if(passedFrom(parseState)) {
                resolveLocation(parseState.baseURL.getReference(), httpClient, sitemapURLAdder, resolvedLocations);
            } else {
                LOG.info("Sitemap Index rejected, too old."
                        + " URL:" + parseState.baseURL.getReference());
            }

            parseState.sitemapIndex = false;
        } else if("url".equalsIgnoreCase(tag)
                && parseState.baseURL.getReference() != null){
            if (isRelaxed(parseState, locationDir)) {
                if(passedFrom(parseState)) {
                    sitemapURLAdder.add(parseState.baseURL);
                }
            } else if (LOG.isDebugEnabled()) {
                LOG.debug("Sitemap URL invalid for location directory."
                        + " URL:" + parseState.baseURL.getReference()
                        + " Location directory: " + locationDir);
            }
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
                        DateTime.parse(value).getMillis());
            } catch (Exception e) {
                LOG.info("Invalid sitemap date: " + value);
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
                LOG.info("Invalid sitemap priority: " + value);
            }
            parseState.priority = false;
        }
    }

    private void parseStartElement(ParseState parseState, String tag) {
        if("sitemap".equalsIgnoreCase(tag)) {
            parseState.sitemapIndex = true;
            parseState.baseURL = new HttpCrawlData("", 0);
        } else if("url".equalsIgnoreCase(tag)){
            parseState.baseURL = new HttpCrawlData("", 0);
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
            String[] sitemapLocations, String urlRoot) {
        Set<String> uniqueLocations = new HashSet<String>();

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

    private static class ParseState {
        private HttpCrawlData baseURL = null;
        private boolean sitemapIndex = false;
        private boolean loc = false;
        private boolean lastmod = false;
        private boolean changefreq = false;
        private boolean priority = false;
    }
}
