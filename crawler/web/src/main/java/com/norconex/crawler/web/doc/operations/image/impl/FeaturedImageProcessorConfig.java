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
package com.norconex.crawler.web.doc.operations.image.impl;

import java.awt.Dimension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.imgscalr.Scalr.Method;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.crawler.core.doc.CrawlDocMetadata;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

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
@Data
@Accessors(chain = true)
public class FeaturedImageProcessorConfig {

    public static final String COLLECTOR_FEATURED_IMAGE_URL =
            CrawlDocMetadata.PREFIX + "featured-image-url";
    public static final String COLLECTOR_FEATURED_IMAGE_PATH =
            CrawlDocMetadata.PREFIX + "featured-image-path";
    public static final String COLLECTOR_FEATURED_IMAGE_INLINE =
            CrawlDocMetadata.PREFIX + "featured-image-inline";

    public static final String DEFAULT_PAGE_CONTENT_TYPE_PATTERN =
            "text/html|application/(xhtml\\+xml|vnd\\.wap.xhtml\\+xml|x-asp)";
    public static final int DEFAULT_IMAGE_CACHE_SIZE = 1000;

    /**
     * Default image cache directory, relative to the crawler working
     * directory.
     */
    public static final String DEFAULT_IMAGE_CACHE_DIR =
            "featuredImageCache";
    /**
     * Default featured image directory, relative to the crawler working
     * directory.
     */
    public static final String DEFAULT_STORAGE_DISK_DIR =
            "featuredImages";

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
        @Getter
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

    private Path imageCacheDir;
    private boolean largest;
    private final List<Storage> storage =
            new ArrayList<>(Arrays.asList(DEFAULT_STORAGE));

    private Path storageDiskDir;
    private StorageDiskStructure storageDiskStructure;
    private Quality scaleQuality = Quality.AUTO;

    private String storageDiskField = COLLECTOR_FEATURED_IMAGE_PATH;
    private String storageInlineField = COLLECTOR_FEATURED_IMAGE_INLINE;
    private String storageUrlField = COLLECTOR_FEATURED_IMAGE_URL;

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
    public FeaturedImageProcessorConfig setStorage(List<Storage> storage) {
        CollectionUtil.setAll(this.storage, storage);
        return this;
    }
}
