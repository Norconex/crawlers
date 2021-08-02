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
package com.norconex.collector.http.processor.impl;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.doc.CrawlDocMetadata;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.processor.IHttpDocumentProcessor;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.IXMLConfigurable;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.Doc;

/**
 * <p>
 * Document processor that extract the "main" image from HTML pages.
 * Since HTML is expected, this class should only be used at
 * pre-import processor. It is possible for this processor to not find any
 * image.
 * </p>
 *
 * <h3>Finding the image</h3>
 * <p>
 * By default this class will get the first image (&lt;img&gt;) matching
 * the minimum size. You can specify you want the largest of all matching
 * ones instead.  In addition, if you know your images to be defined
 * in a special way (e.g., all share the same CSS class), then you can use
 * the "domSelector" to limit to one or a few images. See
 * <a href="https://jsoup.org/cookbook/extracting-data/selector-syntax">
 * JSoup selector-syntax</a> for how to build the "domSelector".
 * </p>
 *
 * <h3>Storing the image</h3>
 * <p>
 * One or more storage method can be specified. Here are
 * the possible storage options:
 * </p>
 * <ul>
 *   <li>
 *     <b>url</b>: Default. The absolute image URL is stored in a
 *     <code>collector.featured-image-url</code> field.
 *     When only this option is set, scaling options and image format
 *     have no effect.
 *   </li>
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
 * {@nx.xml.usage
 * <processor class="com.norconex.collector.http.processor.impl.FeaturedImageProcessor">
 *
 *    <pageContentTypePattern>
 *        (Optional regex to overwrite default matching of HTML pages)
 *    </pageContentTypePattern>
 *
 *    <domSelector>
 *        (Optional CSS-like path matching one or more image elements)
 *    </domSelector>
 *    <minDimensions>
 *        (Minimum pixel size for an image to be considered.
 *         Default is 400x400).
 *    </minDimensions>
 *    <largest>[false|true]</largest>
 *
 *    <imageCacheSize>
 *        (Maximum number of images to cache for faster processing.
 *         Set to 0 to disable caching.)
 *    </imageCacheSize>
 *    <imageCacheDir>
 *        (Directory where to create the image cache)
 *    </imageCacheDir>
 *
 *    <storage>
 *        [url|inline|disk]
 *        (One or more, comma-separated. Default is "url".)
 *    </storage>
 *
 *    <!-- Only applicable for "inline" and "disk" storage: -->
 *    <scaleDimensions>
 *        (Target pixel size the featured image should be scaled to.
 *         Default is 150x150.)
 *    </scaleDimensions>
 *    <scaleStretch>
 *        [false|true]
 *        (Whether to stretch to match scale size. Default keeps aspect ratio.)
 *    </scaleStretch>
 *    <scaleQuality>
 *        [auto|low|medium|high|max]
 *        (Default is "auto", which tries the best balance between quality
 *         and speed based on image size. The lower the quality the faster
 *         it is to scale images.)
 *    </scaleQuality>
 *    <imageFormat>
 *        (Target format of stored image. E.g., "jpg", "png", "gif", "bmp", ...
 *         Default is "png")
 *    </imageFormat>
 *
 *    <!-- Only applicable for "disk" storage: -->
 *    <storageDiskDir structure="[url2path|date|datetime]">
 *        (Path to directory where to store images on disk.)
 *    </storageDiskDir>
 *    <storageDiskField>
 *        (Overwrite default field where to store the image path.
 *         Default is {@value #COLLECTOR_FEATURED_IMAGE_PATH}.)
 *    </storageDiskField>
 *
 *    <!-- Only applicable for "inline" storage: -->
 *    <storageInlineField>
 *        (Overwrite default field where to store the inline image.
 *         Default is {@value #COLLECTOR_FEATURED_IMAGE_INLINE}.)
 *    </storageInlineField>
 *
 *    <!-- Only applicable for "url" storage: -->
 *    <storageUrlField>
 *        (Overwrite default field where to store the image URL.
 *         Default is {@value #COLLECTOR_FEATURED_IMAGE_URL}.)
 *    </storageUrlField>
 *
 * </processor>
 * }
 *
 * When specifying an image size, the format is <code>[width]x[height]</code>
 * or a single value. When a single value is used, that value represents both
 * the width and height (i.e., a square).
 *
 * {@nx.xml.example
 * <preImportProcessors>
 *   <processor class="FeaturedImageProcessor">
 *     <minDimensions>300x400</minDimensions>
 *     <scaleDimensions>50</scaleDimensions>
 *     <imageFormat>jpg</imageFormat>
 *     <scaleQuality>max</scaleQuality>
 *     <storage>inline</storage>
 *   </processor>
 * </preImportProcessors>
 * }
 * <p>
 * The above example extracts the first image being 300x400 or larger, scaling
 * it down to be 50x50 and storing it as an inline JPEG in a document field,
 * preserving aspect ratio and using the best quality possible.
 * </p>
 *
 * @author Pascal Essiembre
 * @since 2.8.0
 */
@SuppressWarnings("javadoc")
public class FeaturedImageProcessor
        implements IHttpDocumentProcessor, IXMLConfigurable {

    private static final Logger LOG = LoggerFactory.getLogger(
            FeaturedImageProcessor.class);

    public static final String COLLECTOR_FEATURED_IMAGE_URL =
            CrawlDocMetadata.PREFIX + "featured-image-url";
    public static final String COLLECTOR_FEATURED_IMAGE_PATH =
            CrawlDocMetadata.PREFIX + "featured-image-path";
    public static final String COLLECTOR_FEATURED_IMAGE_INLINE =
            CrawlDocMetadata.PREFIX + "featured-image-inline";

    public static final String DEFAULT_PAGE_CONTENT_TYPE_PATTERN =
            "text/html|application/(xhtml\\+xml|vnd\\.wap.xhtml\\+xml|x-asp)";
    public static final int DEFAULT_IMAGE_CACHE_SIZE = 1000;
    public static final String DEFAULT_IMAGE_CACHE_DIR =
            "./featuredImageCache";
    public static final String DEFAULT_STORAGE_DISK_DIR =
            "./featuredImages";
    public static final String DEFAULT_IMAGE_FORMAT = "png";
    public static final Dimension DEFAULT_MIN_SIZE = new Dimension(400, 400);
    public static final Dimension DEFAULT_SCALE_SIZE = new Dimension(150, 150);
    public static final Storage DEFAULT_STORAGE = Storage.URL;
    public static final StorageDiskStructure DEFAULT_STORAGE_DISK_STRUCTURE =
            StorageDiskStructure.URL2PATH;

    public enum Storage { URL, INLINE, DISK }
    public enum StorageDiskStructure { URL2PATH, DATE, DATETIME }
    public enum Quality {
        AUTO(Method.AUTOMATIC),
        LOW(Method.SPEED),
        MEDIUM(Method.BALANCED),
        HIGH(Method.QUALITY),
        MAX(Method.ULTRA_QUALITY);
        private final Method scalrMethod;
        Quality(Method scalrMethod) {
            this.scalrMethod = scalrMethod;
        }
    }

    private String pageContentTypePattern = DEFAULT_PAGE_CONTENT_TYPE_PATTERN;
    private String domSelector;
    private Dimension minDimensions = DEFAULT_MIN_SIZE;
    private Dimension scaleDimensions = DEFAULT_SCALE_SIZE;
    private boolean scaleStretch;
    private String imageFormat = DEFAULT_IMAGE_FORMAT;
    private int imageCacheSize = DEFAULT_IMAGE_CACHE_SIZE;
    private Path imageCacheDir = Paths.get(DEFAULT_IMAGE_CACHE_DIR);
    private boolean largest;
    private final List<Storage> storage =
            new ArrayList<>(Arrays.asList(DEFAULT_STORAGE));
    private String storageDiskDir = DEFAULT_STORAGE_DISK_DIR;
    private StorageDiskStructure storageDiskStructure;
    private Quality scaleQuality = Quality.AUTO;

    private String storageDiskField = COLLECTOR_FEATURED_IMAGE_PATH;
    private String storageInlineField = COLLECTOR_FEATURED_IMAGE_INLINE;
    private String storageUrlField = COLLECTOR_FEATURED_IMAGE_URL;

    private static final Map<Path, ImageCache> IMG_CACHES = new HashMap<>();
    private transient boolean initialized;
    private transient ImageCache cache;

    public String getPageContentTypePattern() {
        return pageContentTypePattern;
    }
    public void setPageContentTypePattern(String pageContentTypePattern) {
        this.pageContentTypePattern = pageContentTypePattern;
    }
    public String getDomSelector() {
        return domSelector;
    }
    public void setDomSelector(String domSelector) {
        this.domSelector = domSelector;
    }
    public Dimension getMinDimensions() {
        return minDimensions;
    }
    public void setMinDimensions(int width, int height) {
        setMinDimensions(new Dimension(width, height));
    }
    public void setMinDimensions(Dimension minDimensions) {
        this.minDimensions = minDimensions;
    }
    public Dimension getScaleDimensions() {
        return scaleDimensions;
    }
    public void setScaleDimensions(int width, int height) {
        setScaleDimensions(new Dimension(width, height));
    }
    public void setScaleDimensions(Dimension scaleDimensions) {
        this.scaleDimensions = scaleDimensions;
    }
    public boolean isScaleStretch() {
        return scaleStretch;
    }
    public void setScaleStretch(boolean scaleStretch) {
        this.scaleStretch = scaleStretch;
    }
    public String getImageFormat() {
        return imageFormat;
    }
    public void setImageFormat(String imageFormat) {
        this.imageFormat = imageFormat;
    }
    public int getImageCacheSize() {
        return imageCacheSize;
    }
    public void setImageCacheSize(int imageCacheSize) {
        this.imageCacheSize = imageCacheSize;
    }
    public Path getImageCacheDir() {
        return imageCacheDir;
    }
    public void setImageCacheDir(Path imageCacheDir) {
        this.imageCacheDir = imageCacheDir;
    }
    public boolean isLargest() {
        return largest;
    }
    public void setLargest(boolean largest) {
        this.largest = largest;
    }
    /**
     * Gets the storage mechanisms.
     * @return storage mechanisms
     */
    public List<Storage> getStorage() {
        return Collections.unmodifiableList(storage);
    }
    /**
     * Sets the storage mechanisms.
     * @param storage storage mechanisms
     */
    public void setStorage(Storage... storage) {
        CollectionUtil.setAll(this.storage, storage);
    }
    /**
     * Sets the storage mechanisms.
     * @param storage storage mechanisms
     * @since 3.0.0
     */
    public void setStorage(List<Storage> storage) {
        CollectionUtil.setAll(this.storage, storage);
    }
    public String getStorageDiskDir() {
        return storageDiskDir;
    }
    public void setStorageDiskDir(String storageDiskDir) {
        this.storageDiskDir = storageDiskDir;
    }
    public StorageDiskStructure getStorageDiskStructure() {
        return storageDiskStructure;
    }
    public void setStorageDiskStructure(
            StorageDiskStructure storageDiskStructure) {
        this.storageDiskStructure = storageDiskStructure;
    }
    public String getStorageDiskField() {
        return storageDiskField;
    }
    public void setStorageDiskField(String storageDiskField) {
        this.storageDiskField = storageDiskField;
    }
    public String getStorageInlineField() {
        return storageInlineField;
    }
    public void setStorageInlineField(String storageInlineField) {
        this.storageInlineField = storageInlineField;
    }
    public String getStorageUrlField() {
        return storageUrlField;
    }
    public void setStorageUrlField(String storageUrlField) {
        this.storageUrlField = storageUrlField;
    }
    public Quality getScaleQuality() {
        return scaleQuality;
    }
    public void setScaleQuality(Quality scaleQuality) {
        this.scaleQuality = scaleQuality;
    }
    @Override
    public void processDocument(HttpFetchClient fetcher, Doc doc) {
        ensureInit();

        // Return if not valid content type
        if (StringUtils.isNotBlank(pageContentTypePattern)
                && !Objects.toString(doc.getDocInfo().getContentType()).matches(
                        pageContentTypePattern)) {
            return;
        }

        try {
            // Obtain the image
            Document dom = Jsoup.parse(doc.getInputStream(),
                    doc.getDocInfo().getContentEncoding(), doc.getReference());
            ScaledImage img = findFeaturedImage(dom, fetcher, largest);

            // Save the image
            if (img != null) {
                LOG.debug("Featured image is \"{}\" for \"{}\"",
                        img.getUrl(), doc.getReference());
                storeImage(img, doc);
            } else {
                LOG.debug("No featured image found for: {}",
                        doc.getReference());
            }
        } catch (IOException e) {
            LOG.error("Could not extract main image from document: {}",
                    doc.getReference(), e);
        }
    }

    private void storeImage(ScaledImage img, Doc doc)
            throws IOException {
        if (storage.contains(Storage.URL)) {
            doc.getMetadata().add(Objects.toString(storageUrlField,
                    COLLECTOR_FEATURED_IMAGE_URL), img.getUrl());
        }
        if (storage.contains(Storage.INLINE)) {
            doc.getMetadata().add(Objects.toString(storageInlineField,
                    COLLECTOR_FEATURED_IMAGE_INLINE),
                    img.toHTMLInlineString(imageFormat));
        }
        if (storage.contains(Storage.DISK)) {
            File diskDir = new File(storageDiskDir);
            File imageFile = null;
            if (storageDiskStructure == StorageDiskStructure.DATE) {
                String fileId = Long.toString(TimeIdGenerator.next());
                imageFile = new File(FileUtil.createDateDirs(
                        diskDir), fileId + "." + imageFormat);
            } else if (storageDiskStructure == StorageDiskStructure.DATETIME) {
                String fileId = Long.toString(TimeIdGenerator.next());
                imageFile = new File(FileUtil.createDateTimeDirs(
                        diskDir), fileId + "." + imageFormat);
            } else {
                imageFile = new File(FileUtil.createURLDirs(
                        diskDir, img.getUrl(), true).getAbsolutePath()
                        + "." + imageFormat);
            }
            ImageIO.write(img.getImage(), imageFormat, imageFile);
            doc.getMetadata().add(Objects.toString(
                    storageDiskField, COLLECTOR_FEATURED_IMAGE_PATH),
                    imageFile.getAbsolutePath());
        }
    }

    private boolean savingImage() {
        return storage.contains(Storage.INLINE)
                || storage.contains(Storage.DISK);
    }

    private ScaledImage findFeaturedImage(
            Document dom, HttpFetchClient fetcher, boolean largest) {
        Elements els;
        if (StringUtils.isNotBlank(domSelector)) {
            els = dom.select(domSelector);
        } else {
            els = dom.getElementsByTag("img");
        }
        ScaledImage largestImg = null;
        for (Iterator<Element> it = els.iterator(); it.hasNext();) {
            Element el = it.next();
            String imgURL = el.absUrl("src");
            ScaledImage img = null;
            if (StringUtils.isNotBlank(imgURL)) {
                img = getImage(fetcher, imgURL);
            }
            if (img == null) {
                continue;
            }
            if (minDimensions == null || img.contains(minDimensions)) {
                if (!largest) {
                    return img;
                } else if (largestImg == null
                        || img.getArea() > largestImg.getArea()) {
                    largestImg = img;
                }
            }
        }
        return largestImg;
    }

    private synchronized void ensureInit() {
        if (initialized) {
            return;
        }
        if (imageCacheSize != 0) {
            ImageCache imgCache = IMG_CACHES.get(imageCacheDir);
            if (imgCache == null) {
                imgCache = new ImageCache(imageCacheSize, imageCacheDir);
                IMG_CACHES.put(imageCacheDir, imgCache);
            }
            this.cache = imgCache;
        }
        this.initialized = true;
    }

    private ScaledImage getImage(HttpFetchClient fetcher, String url) {
        try {
            ScaledImage img = null;
            if (cache != null) {
                img = cache.getImage(url);
            }
            if (img == null) {
                BufferedImage bi = fetchImage(fetcher, url);
                if (bi == null) {
                    return null;
                }
                Dimension dim = new Dimension(bi.getWidth(), bi.getHeight());
                bi = scale(bi);

                img = new ScaledImage(url, dim, bi);
                if (cache != null) {
                    cache.setImage(img);
                }
            }
            return img;
        } catch (Exception e) {
            LOG.debug("Could not load image: " + url, e);
        }
        return null;
    }

    private BufferedImage scale(BufferedImage origImg) {

        // If scale is not needed (URL storage only), make image tiny.
        if (!savingImage()) {
            return new BufferedImage(1, 1, origImg.getType());
        }

        // If scale is null, return as is (no scaling).
        if (scaleDimensions == null) {
            return origImg;
        }

        // if image is smaller than minimum dimension... cache empty image
        if (minDimensions != null &&
                (origImg.getWidth() < minDimensions.getWidth()
                        || origImg.getHeight() < minDimensions.getHeight())) {
            return new BufferedImage(1, 1, origImg.getType());
        }

        int scaledWidth = (int) scaleDimensions.getWidth();
        int scaledHeight = (int) scaleDimensions.getHeight();

        Mode mode = Mode.AUTOMATIC;
        if (scaleStretch) {
            mode = Mode.FIT_EXACT;
        }
        Method method = Method.AUTOMATIC;
        if (scaleQuality != null) {
            method = scaleQuality.scalrMethod;
        }
        BufferedImage newImg =
                Scalr.resize(origImg, method, mode, scaledWidth, scaledHeight);

        // Remove alpha layer for formats not supporting it. This prevents
        // some files from having a colored background (instead of transparency)
        // or to not be saved properly (e.g. png to bmp).
        if (EqualsUtil.equalsNoneIgnoreCase(imageFormat, "png", "gif")) {
            BufferedImage fixedImg = new BufferedImage(
                    newImg.getWidth(), newImg.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            fixedImg.createGraphics().drawImage(
                    newImg, 0, 0, Color.WHITE, null);
            newImg = fixedImg;
        }
        return newImg;
    }

    // make synchronized?
    private BufferedImage fetchImage(HttpFetchClient fetcher, String url) {
        try {
            URI uri = HttpURL.toURI(url);
            CrawlDoc doc = new CrawlDoc(new HttpDocInfo(uri.toString()),
                    fetcher.getStreamFactory().newInputStream());

            IHttpFetchResponse resp = fetcher.fetch(doc, HttpMethod.GET);
            if (resp != null
                    && resp.getCrawlState() != null
                    && resp.getCrawlState().isGoodState()) {
                BufferedImage bufImage = ImageIO.read(doc.getInputStream());
                doc.dispose();
                if (bufImage == null) {
                    LOG.debug("Image could not be read: '{}.' "
                            + "Detected format: '{}'.", url,
                            doc.getDocInfo().getContentType());
                }
                return bufImage;
            }
        } catch (IOException e) {
            LOG.debug("Could not load image: {}", url, e);
        }
        LOG.debug(
                "Image was not recognized or could not be downloaded: {}", url);
        return null;
    }

    @Override
    public void loadFromXML(XML xml) {
        setPageContentTypePattern(xml.getString(
                "pageContentTypePattern", pageContentTypePattern));
        setDomSelector(xml.getString("domSelector", domSelector));
        setMinDimensions(xml.getDimension("minDimensions", minDimensions));
        setScaleDimensions(xml.getDimension(
                "scaleDimensions", scaleDimensions));
        setScaleStretch(xml.getBoolean("scaleStretch", scaleStretch));
        setImageFormat(xml.getString("imageFormat", imageFormat));
        setImageCacheSize(xml.getInteger("imageCacheSize", imageCacheSize));
        setImageCacheDir(xml.getPath("imageCacheDir", imageCacheDir));
        setLargest(xml.getBoolean("largest", largest));
        setScaleQuality(xml.getEnum(
                "scaleQuality", Quality.class, scaleQuality));
        setStorage(xml.getDelimitedEnumList("storage", Storage.class, storage));
        setStorageDiskDir(xml.getString("storageDiskDir", storageDiskDir));
        setStorageDiskStructure(
                xml.getEnum("storageDiskDir/@structure",
                        StorageDiskStructure.class, storageDiskStructure));
        setStorageDiskField(xml.getString(
                "storageDiskField", storageDiskField));
        setStorageInlineField(xml.getString(
                "storageInlineField", getStorageInlineField()));
        setStorageUrlField(xml.getString(
                "storageUrlField", getStorageUrlField()));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.addElement("pageContentTypePattern", pageContentTypePattern);
        xml.addElement("domSelector", domSelector);
        xml.addElement("minDimensions", minDimensions);
        xml.addElement("scaleDimensions", scaleDimensions);
        xml.addElement("scaleStretch", isScaleStretch());
        xml.addElement("imageFormat", imageFormat);
        xml.addElement("imageCacheSize", imageCacheSize);
        xml.addElement("imageCacheDir", imageCacheDir);
        xml.addElement("largest", largest);
        xml.addElement("scaleQuality", scaleQuality);
        xml.addDelimitedElementList("storage", storage);
        xml.addElement("storageDiskDir", storageDiskDir)
                .setAttribute("structure", storageDiskStructure);
        xml.addElement("storageDiskField", storageDiskField);
        xml.addElement("storageInlineField", storageInlineField);
        xml.addElement("storageUrlField", storageUrlField);
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }
}
