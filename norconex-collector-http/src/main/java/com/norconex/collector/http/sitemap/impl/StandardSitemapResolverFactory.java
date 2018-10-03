/* Copyright 2010-2018 Norconex Inc.
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.crawler.Crawler;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.crawler.CrawlerLifeCycleListener;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.sitemap.ISitemapResolver;
import com.norconex.collector.http.sitemap.ISitemapResolverFactory;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;

/**
 * <p>
 * Factory used to created {@link StandardSitemapResolver} instances.
 * Refer to {@link StandardSitemapResolver} for resolution logic.
 * </p>
 *
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;sitemapResolverFactory ignore="[false|true]" lenient="[false|true]"
 *     class="com.norconex.collector.http.sitemap.impl.StandardSitemapResolverFactory"&gt;
 *  <!-- TODO: Have this option?
 *     &lt;tempDir&gt;(where to store temp files)&lt;/tempDir&gt;
 *    -->
 *     &lt;path&gt;
 *       (Optional path relative to URL root for a sitemap. Use a single empty
 *        "path" tag to rely instead on any sitemaps specified as start URLs or
 *        defined in robots.txt, if enabled.  Not specifying any path tags
 *        falls back to trying to locate sitemaps using default paths.)
 *     &lt;/path&gt;
 *     (... repeat path tag as needed ...)
 *  &lt;/sitemapResolverFactory&gt;
 * </pre>
 *
 * <h4>Usage example:</h4>
 * <p>
 * The following ignores sitemap files present on web sites.
 * </p>
 * <pre>
 *  &lt;sitemapResolverFactory ignore="true"/&gt;
 * </pre>
 *
 * @author Pascal Essiembre
 * @see StandardSitemapResolver
 */
public class StandardSitemapResolverFactory
        extends CrawlerLifeCycleListener
        implements ISitemapResolverFactory, IXMLConfigurable {

    private static final Logger LOG = LoggerFactory.getLogger(
            StandardSitemapResolverFactory.class);

    private Path tempDir;
    private Path workDir;
    private final List<String> sitemapPaths =
            new ArrayList<>(StandardSitemapResolver.DEFAULT_SITEMAP_PATHS);
    private boolean lenient;


    @Override
    protected void crawlerStartup(CrawlerEvent<Crawler> event) {
        if (tempDir == null) {
            tempDir = event.getSource().getTempDir();
        }
        if (workDir == null) {
            workDir = event.getSource().getWorkDir();
        }
    }
//    @Override
//    protected void crawlerShutdown(CrawlerEvent<Crawler> event) {
//    }

    @Override
    public ISitemapResolver createSitemapResolver(
            HttpCrawlerConfig config, boolean resume) {

        Path resolvedTempDir = tempDir;
        if (resolvedTempDir == null) {
            resolvedTempDir = FileUtils.getTempDirectory().toPath();
            LOG.info("Sitemap temporary directory: {}", resolvedTempDir);
        }
        resolvedTempDir = resolvedTempDir.resolve("sitemap");
        StandardSitemapResolver sr = new StandardSitemapResolver(
                resolvedTempDir, new SitemapStore(
                        config, HttpCrawler.get().getWorkDir(), resume));
        sr.setLenient(lenient);
        sr.setSitemapPaths(sitemapPaths);
        return sr;
    }

    /**
     * Gets the URL paths, relative to the URL root, from which to try
     * locate and resolve sitemaps. Default paths are
     * "/sitemap.xml" and "/sitemap-index.xml".
     * @return sitemap paths.
     * @since 2.3.0
     */
    public List<String> getSitemapPaths() {
        return sitemapPaths;
    }
    /**
     * Sets the URL paths, relative to the URL root, from which to try
     * locate and resolve sitemaps.
     * @param sitemapPaths sitemap paths.
     * @since 2.3.0
     */
    public void setSitemapPaths(String... sitemapPaths) {
        CollectionUtil.setAll(this.sitemapPaths, sitemapPaths);
    }
    /**
     * Sets the URL paths, relative to the URL root, from which to try
     * locate and resolve sitemaps.
     * @param sitemapPaths sitemap paths.
     * @since 3.0.0
     */
    public void setSitemapPaths(List<String> sitemapPaths) {
        CollectionUtil.setAll(this.sitemapPaths, sitemapPaths);
    }

    public boolean isLenient() {
        return lenient;
    }
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    /**
     * Gets the directory where sitemap files are temporary stored
     * before they are parsed.  When <code>null</code> (default), temporary
     * files are created directly under {@link HttpCrawlerConfig#getWorkDir()}.
     * the crawler working directory is also undefined, it will use the
     * system temporary directory, as returned by
     * {@link FileUtils#getTempDirectory()}.
     * @return directory where temporary files are written
     * @since 2.3.0
     */
    public Path getTempDir() {
        return tempDir;
    }
    /**
     * Sets the temporary directory where sitemap files are temporary stored
     * before they are parsed.
     * @param tempDir directory where temporary files are written
     * @since 2.3.0
     */
    public void setTempDir(Path tempDir) {
        this.tempDir = tempDir;
    }

    // Since 3.0.0
    public Path getWorkDir() {
        return workDir;
    }
    // Since 3.0.0
    public void setWorkDir(Path workDir) {
        this.workDir = workDir;
    }

    @Override
    public void loadFromXML(XML xml) {
//        setTempDir(xml.getPath("tempDir", tempDir));
        setLenient(xml.getBoolean("@lenient", lenient));

        //TODO make sure null can be set to clear the paths instead of this hack
        List<String> paths = xml.getStringList("path");
        if (paths.size() == 1 && paths.get(0).equals("")) {
            setSitemapPaths((List<String>) null);
        } else if (!paths.isEmpty()) {
            setSitemapPaths(paths);
        }
//        setSitemapPaths(xml.getStringList("path", sitemapPaths));
//
//
//
//        String[] paths = xml.getList(
//                "path").toArray(ArrayUtils.EMPTY_STRING_ARRAY);
//        if (!ArrayUtils.isEmpty(paths)) {
//            // not empty but if empty after removing blank ones, consider
//            // empty (we want no path)
//            List<String> cleanPaths = new ArrayList<>(paths.length);
//            for (String path : paths) {
//                if (StringUtils.isNotBlank(path)) {
//                    cleanPaths.add(path);
//                }
//            }
//            if (cleanPaths.isEmpty()) {
//                setSitemapPaths(ArrayUtils.EMPTY_STRING_ARRAY);
//            } else {
//                setSitemapPaths(
//                        cleanPaths.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
//            }
//        }
    }

    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("lenient", lenient);
//        xml.addElement("tempDir", getTempDir());
//        xml.addElementList("path", sitemapPaths);

        if (sitemapPaths.isEmpty()) {
            xml.addElement("path", "");
        } else {
            xml.addElementList("path", sitemapPaths);
        }

        //TODO make sure null can be set to clear the paths
        // could be done for list if always wrapping lists in parent tag
        // that when present but empty, means null.


//        if (ArrayUtils.isEmpty(sitemapPaths)) {
//            writer.writeStartElement("path");
//            writer.writeCharacters("");
//            writer.writeEndElement();
//        } else {
//            for (String path : sitemapPaths) {
//                writer.writeStartElement("path");
//                writer.writeCharacters(path);
//                writer.writeEndElement();
//            }
//        }
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(
                this, other, "workDir", "tempDir");
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, "workDir", "tempDir");
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(this,
                ToStringStyle.SHORT_PREFIX_STYLE).setExcludeFieldNames(
                        "workDir", "tempDir").toString();
    }
}
