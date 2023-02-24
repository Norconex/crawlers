/* Copyright 2017-2023 Norconex Inc.
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
package com.norconex.crawler.web.processor.impl;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.imageio.ImageIO;

import org.apache.commons.lang3.StringUtils;
import org.imgscalr.Scalr;
import org.imgscalr.Scalr.Method;
import org.imgscalr.Scalr.Mode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.web.doc.HttpDocRecord;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.processor.HttpDocumentProcessor;
import com.norconex.importer.doc.Doc;

import lombok.Data;

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
 * <processor class="com.norconex.crawler.web.processor.impl.FeaturedImageProcessor">
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
 * @since 2.8.0
 */
@SuppressWarnings("javadoc")
@Data
public class FeaturedImageProcessor
        implements HttpDocumentProcessor, XMLConfigurable {

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


    //TODO THESE two default should be removed to default to subdirs in
    // crawler "workDir" instead.  At least the cache one.  For the image
    // storage, default could be current directory... but not ideal.
    // Could also throw an error by making it mandatory if
    // storage DISK is selected
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

    //TODO use DocImageHandler from Importer reuse its Javadoc
    // and XML write/read where applicable.

    private String pageContentTypePattern = DEFAULT_PAGE_CONTENT_TYPE_PATTERN;
    private String domSelector;
    private Dimension minDimensions = DEFAULT_MIN_SIZE;
    private Dimension scaleDimensions = DEFAULT_SCALE_SIZE;
    private boolean scaleStretch;
    private String imageFormat = DEFAULT_IMAGE_FORMAT;
    private int imageCacheSize = DEFAULT_IMAGE_CACHE_SIZE;

    //TODO default to crawler workDir
    private Path imageCacheDir = Paths.get(DEFAULT_IMAGE_CACHE_DIR);
    private boolean largest;
    private final List<Storage> storage =
            new ArrayList<>(Arrays.asList(DEFAULT_STORAGE));

    //TODO make a Path object instead of String, and default to crawler workDir
    private String storageDiskDir = DEFAULT_STORAGE_DISK_DIR;
    private StorageDiskStructure storageDiskStructure;
    private Quality scaleQuality = Quality.AUTO;

    private String storageDiskField = COLLECTOR_FEATURED_IMAGE_PATH;
    private String storageInlineField = COLLECTOR_FEATURED_IMAGE_INLINE;
    private String storageUrlField = COLLECTOR_FEATURED_IMAGE_URL;

    private static final Map<Path, ImageCache> IMG_CACHES = new HashMap<>();
    private boolean initialized;
    private ImageCache cache;

    //TODO add option to process embedded images (base 64)

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
     * @since 3.0.0
     */
    public void setStorage(List<Storage> storage) {
        CollectionUtil.setAll(this.storage, storage);
    }

    @Override
    public void processDocument(HttpFetcher fetcher, Doc doc) {
        ensureInit();

        // Return if not valid content type
        if (StringUtils.isNotBlank(pageContentTypePattern)
                && !Objects.toString(doc.getDocRecord().getContentType()).matches(
                        pageContentTypePattern)) {
            return;
        }

        try {
            // Obtain the image
            var dom = Jsoup.parse(doc.getInputStream(),
                    doc.getDocRecord().getContentEncoding(), doc.getReference());
            var img = findFeaturedImage(dom, fetcher, largest);

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
            var diskDir = new File(storageDiskDir);
            File imageFile = null;
            if (storageDiskStructure == StorageDiskStructure.DATE) {
                var fileId = Long.toString(TimeIdGenerator.next());
                imageFile = new File(FileUtil.createDateDirs(
                        diskDir), fileId + "." + imageFormat);
            } else if (storageDiskStructure == StorageDiskStructure.DATETIME) {
                var fileId = Long.toString(TimeIdGenerator.next());
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
            Document dom, HttpFetcher fetcher, boolean largest) {
        Elements els;
        if (StringUtils.isNotBlank(domSelector)) {
            els = dom.select(domSelector);
        } else {
            els = dom.getElementsByTag("img");
        }
        ScaledImage largestImg = null;
        for (Element el : els) {
            var imgURL = el.absUrl("src");
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
                }
                if (largestImg == null
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
            var imgCache = IMG_CACHES.get(imageCacheDir);
            if (imgCache == null) {
                imgCache = new ImageCache(imageCacheSize, imageCacheDir);
                IMG_CACHES.put(imageCacheDir, imgCache);
            }
            cache = imgCache;
        }
        initialized = true;
    }

    private ScaledImage getImage(HttpFetcher fetcher, String url) {
        try {
            ScaledImage img = null;
            if (cache != null) {
                img = cache.getImage(url);
            }
            if (img == null) {
                var bi = fetchImage(fetcher, url);
                if (bi == null) {
                    return null;
                }
                var dim = new Dimension(bi.getWidth(), bi.getHeight());
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

        var scaledWidth = (int) scaleDimensions.getWidth();
        var scaledHeight = (int) scaleDimensions.getHeight();

        var mode = Mode.AUTOMATIC;
        if (scaleStretch) {
            mode = Mode.FIT_EXACT;
        }
        var method = Method.AUTOMATIC;
        if (scaleQuality != null) {
            method = scaleQuality.scalrMethod;
        }
        var newImg =
                Scalr.resize(origImg, method, mode, scaledWidth, scaledHeight);

        // Remove alpha layer for formats not supporting it. This prevents
        // some files from having a colored background (instead of transparency)
        // or to not be saved properly (e.g. png to bmp).
        if (EqualsUtil.equalsNoneIgnoreCase(imageFormat, "png", "gif")) {
            var fixedImg = new BufferedImage(
                    newImg.getWidth(), newImg.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            fixedImg.createGraphics().drawImage(
                    newImg, 0, 0, Color.WHITE, null);
            newImg = fixedImg;
        }
        return newImg;
    }

    // make synchronized?
    private BufferedImage fetchImage(HttpFetcher fetcher, String url) {
        try {
            var uri = HttpURL.toURI(url);
            var doc = new CrawlDoc(new HttpDocRecord(uri.toString()));

            FetchResponse resp = fetcher.fetch(
                    new HttpFetchRequest(doc, HttpMethod.GET));
            if (resp != null
                    && resp.getCrawlDocState() != null
                    && resp.getCrawlDocState().isGoodState()) {
                var bufImage = ImageIO.read(doc.getInputStream());
                doc.dispose();
                if (bufImage == null) {
                    LOG.debug("Image could not be read: '{}.' "
                            + "Detected format: '{}'.", url,
                            doc.getDocRecord().getContentType());
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
}
