/* Copyright 2017-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.image.impl;

import static com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageProcessorConfig.COLLECTOR_FEATURED_IMAGE_INLINE;
import static com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageProcessorConfig.COLLECTOR_FEATURED_IMAGE_PATH;
import static com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageProcessorConfig.COLLECTOR_FEATURED_IMAGE_URL;
import static com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageProcessorConfig.DEFAULT_IMAGE_CACHE_DIR;
import static com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageProcessorConfig.DEFAULT_STORAGE_DISK_DIR;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.endsWithIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.img.MutableImage;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.operations.DocumentConsumer;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.event.listeners.CrawlerLifeCycleListener;
import com.norconex.crawler.core.fetch.FetchResponse;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageProcessorConfig.Storage;
import com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageProcessorConfig.StorageDiskStructure;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.importer.doc.Doc;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

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
 * <processor class="com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageProcessor">
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
@EqualsAndHashCode
@ToString
@Slf4j
public class FeaturedImageProcessor
        extends
            CrawlerLifeCycleListener
        implements
            DocumentConsumer,
            Configurable<FeaturedImageProcessorConfig> {

    //TODO add ability to extract from popular HTML <meta> for
    // featured image
    //TODO use DocImageHandler from Importer?
    //TODO add option to process embedded images (base 64)

    @Getter
    private final FeaturedImageProcessorConfig configuration =
            new FeaturedImageProcessorConfig();

    private static final Map<Path, ImageCache> IMG_CACHES = new HashMap<>();

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    private ImageCache cache;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    private Path resolvedImageCacheDir;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    private Path resolvedStorageDiskDir;

    //--- Init. ----------------------------------------------------------------

    @Override
    protected void onCrawlerRunBegin(CrawlerEvent event) {
        var workDir = event.getSource().getWorkDir();

        // Initialize image cache directory
        if (configuration.getImageCacheSize() > 0) {
            resolvedImageCacheDir = ofNullable(configuration.getImageCacheDir())
                    .orElseGet(() -> workDir.resolve(DEFAULT_IMAGE_CACHE_DIR));
            try {
                Files.createDirectories(resolvedImageCacheDir);
                LOG.info("Featured image cache directory: {}",
                        resolvedImageCacheDir);
            } catch (IOException e) {
                throw new CrawlerException(
                        "Could not create featured image cache directory.", e);
            }
            cache = IMG_CACHES.computeIfAbsent(
                    resolvedImageCacheDir, dir -> new ImageCache(
                            configuration.getImageCacheSize(),
                            resolvedImageCacheDir));
        }

        // Initialize image directory
        if (configuration.getStorage().contains(Storage.DISK)) {
            resolvedStorageDiskDir = ofNullable(
                    configuration.getStorageDiskDir()).orElseGet(
                            () -> workDir.resolve(DEFAULT_STORAGE_DISK_DIR));
            try {
                Files.createDirectories(resolvedStorageDiskDir);
                LOG.info("Featured image storage directory: {}",
                        resolvedStorageDiskDir);
            } catch (IOException e) {
                throw new CrawlerException(
                        "Could not create featured image storage directory.",
                        e);
            }
        }
    }

    //--- Process Document -----------------------------------------------------

    @Override
    public void accept(Fetcher<?, ?> f, CrawlDoc doc) {

        var fetcher = (HttpFetcher) f;


        // Return if not valid content type
        if (StringUtils.isNotBlank(configuration.getPageContentTypePattern())
                && !Objects.toString(doc.getDocContext().getContentType())
                        .matches(configuration.getPageContentTypePattern())) {
            return;
        }

        try {
            // Obtain the image
            var dom = Jsoup.parse(
                    doc.getInputStream(),
                    ofNullable(doc.getDocContext().getCharset())
                        .map(Charset::toString)
                        .orElse(null),
                    doc.getReference());
            var img = findFeaturedImage(
                    dom, fetcher, configuration.isLargest());

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
            LOG.error("Could not extract featured image from document: {}",
                    doc.getReference(), e);
        }
    }

    private void storeImage(FeaturedImage img, Doc doc)
            throws IOException {
        var imgFormat = configuration.getImageFormat();
        if (configuration.getStorage().contains(Storage.URL)) {
            doc.getMetadata().add(Objects.toString(
                    configuration.getStorageUrlField(),
                    COLLECTOR_FEATURED_IMAGE_URL), img.getUrl());
        }
        if (configuration.getStorage().contains(Storage.INLINE)) {
            doc.getMetadata().add(Objects.toString(
                    configuration.getStorageInlineField(),
                    COLLECTOR_FEATURED_IMAGE_INLINE),
                    img.toHTMLInlineString(imgFormat));
        }
        if (configuration.getStorage().contains(Storage.DISK)) {
            Path imageFile = null;
            if (configuration.getStorageDiskStructure()
                    == StorageDiskStructure.DATE) {
                var fileId = Long.toString(TimeIdGenerator.next());
                imageFile = FileUtil.createDateDirs(
                        resolvedStorageDiskDir.toFile()).toPath().resolve(
                                fileId + "." + imgFormat);
            } else if (configuration.getStorageDiskStructure() ==
                    StorageDiskStructure.DATETIME) {
                var fileId = Long.toString(TimeIdGenerator.next());
                imageFile = FileUtil.createDateTimeDirs(
                        resolvedStorageDiskDir.toFile()).toPath().resolve(
                                fileId + "." + imgFormat);
            } else {
                String filePath = null;
                if (StringUtils.startsWith(img.getUrl(), "data:")) {
                    filePath = FileUtil.createURLDirs(
                            resolvedStorageDiskDir.toFile(),
                            doc.getReference() + "/base64-"
                                    + img.getUrl().hashCode(), true)
                                            .getAbsolutePath();
                } else {
                    filePath = FileUtil.createURLDirs(
                            resolvedStorageDiskDir.toFile(), img.getUrl(), true)
                                    .getAbsolutePath();
                }
                if (!endsWithIgnoreCase(
                        filePath, "." + imgFormat)) {
                    filePath += "." + imgFormat;
                }
                imageFile = Paths.get(filePath);
            }
            ImageIO.write(img.getImage(), imgFormat, imageFile.toFile());
            doc.getMetadata().add(Objects.toString(
                    configuration.getStorageDiskField(),
                    COLLECTOR_FEATURED_IMAGE_PATH),
                    imageFile.toFile().getAbsolutePath());
        }
    }

    private boolean savingImage() {
        return configuration.getStorage().contains(Storage.INLINE)
                || configuration.getStorage().contains(Storage.DISK);
    }

    private FeaturedImage findFeaturedImage(
            Document dom, HttpFetcher fetcher, boolean largest) {
        var els = isNotBlank(configuration.getDomSelector())
                ? dom.select(configuration.getDomSelector())
                : dom.getElementsByTag("img");

        FeaturedImage largestImg = null;
        for (Element el : els) {
            var img = ofNullable(el.absUrl("src"))
                    .filter(StringUtils::isNotBlank)
                    .map(url ->  getImage(fetcher, url))
                    .orElse(null);
            if (img == null) {
                continue;
            }

            if (configuration.getMinDimensions() == null
                    || img.contains(configuration.getMinDimensions())) {
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

    private FeaturedImage getImage(HttpFetcher fetcher, String url) {
        try {
            FeaturedImage img = null;
            if (cache != null) {
                img = cache.getImage(url);
            }
            if (img == null) {
                if (url.matches("(?i)^data:image/[^;]*;base64,.*$")) {
                    var mutableImg = MutableImage.fromBase64String(url);
                    if (mutableImg == null) {
                        return null;
                    }
                    img = new FeaturedImage(url, mutableImg.getDimension(),
                            mutableImg.toImage());
                } else  {
                    var bi = fetchImage(fetcher, url);
                    if (bi == null) {
                        return null;
                    }
                    var dim = new Dimension(bi.getWidth(), bi.getHeight());
                    bi = scale(bi);

                    img = new FeaturedImage(url, dim, bi);
                }
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
        if (configuration.getScaleDimensions() == null) {
            return origImg;
        }

        // if image is smaller than minimum dimension... cache empty image
        var minDims = configuration.getMinDimensions();
        if (minDims != null && (origImg.getWidth() < minDims.getWidth()
                || origImg.getHeight() < minDims.getHeight())) {
            return new BufferedImage(1, 1, origImg.getType());
        }

        var scaledWidth = (int) configuration.getScaleDimensions().getWidth();
        var scaledHeight = (int) configuration.getScaleDimensions().getHeight();

        var mode = Mode.AUTOMATIC;
        if (configuration.isScaleStretch()) {
            mode = Mode.FIT_EXACT;
        }
        var method = Method.AUTOMATIC;
        if (configuration.getScaleQuality() != null) {
            method = configuration.getScaleQuality().getScalrMethod();
        }

        var newImg =
                Scalr.resize(origImg, method, mode, scaledWidth, scaledHeight);
        // Remove alpha layer for formats not supporting it. This prevents
        // some files from having a colored background (instead of transparency)
        // or to not be saved properly (e.g. png to bmp).
        if (EqualsUtil.equalsNoneIgnoreCase(
                configuration.getImageFormat(), "png", "gif")) {
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
            var doc = new CrawlDoc(new WebCrawlDocContext(uri.toString()));

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
                            doc.getDocContext().getContentType());
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

//    @Override
//    public void loadFromXML(XML xml) {
//        setPageContentTypePattern(xml.getString(
//                "pageContentTypePattern", pageContentTypePattern));
//        setDomSelector(xml.getString("domSelector", domSelector));
//        setMinDimensions(xml.getDimension("minDimensions", minDimensions));
//        setScaleDimensions(xml.getDimension(
//                "scaleDimensions", scaleDimensions));
//        setScaleStretch(xml.getBoolean("scaleStretch", scaleStretch));
//        setImageFormat(xml.getString("imageFormat", imageFormat));
//        setImageCacheSize(xml.getInteger("imageCacheSize", imageCacheSize));
//        setImageCacheDir(xml.getPath("imageCacheDir", imageCacheDir));
//        setLargest(xml.getBoolean("largest", largest));
//        setScaleQuality(xml.getEnum(
//                "scaleQuality", Quality.class, scaleQuality));
//        setStorage(xml.getDelimitedEnumList("storage", Storage.class, storage));
//        setStorageDiskDir(xml.getPath("storageDiskDir", storageDiskDir));
//        setStorageDiskStructure(
//                xml.getEnum("storageDiskDir/@structure",
//                        StorageDiskStructure.class, storageDiskStructure));
//        setStorageDiskField(xml.getString(
//                "storageDiskField", storageDiskField));
//        setStorageInlineField(xml.getString(
//                "storageInlineField", getStorageInlineField()));
//        setStorageUrlField(xml.getString(
//                "storageUrlField", getStorageUrlField()));
//    }
//    @Override
//    public void saveToXML(XML xml) {
//        xml.addElement("pageContentTypePattern", pageContentTypePattern);
//        xml.addElement("domSelector", domSelector);
//        xml.addElement("minDimensions", minDimensions);
//        xml.addElement("scaleDimensions", scaleDimensions);
//        xml.addElement("scaleStretch", isScaleStretch());
//        xml.addElement("imageFormat", imageFormat);
//        xml.addElement("imageCacheSize", imageCacheSize);
//        xml.addElement("imageCacheDir", imageCacheDir);
//        xml.addElement("largest", largest);
//        xml.addElement("scaleQuality", scaleQuality);
//        xml.addDelimitedElementList("storage", storage);
//        xml.addElement("storageDiskDir", storageDiskDir)
//                .setAttribute("structure", storageDiskStructure);
//        xml.addElement("storageDiskField", storageDiskField);
//        xml.addElement("storageInlineField", storageInlineField);
//        xml.addElement("storageUrlField", storageUrlField);
//    }
}
