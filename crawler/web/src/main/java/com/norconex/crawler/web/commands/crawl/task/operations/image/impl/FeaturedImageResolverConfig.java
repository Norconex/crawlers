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
package com.norconex.crawler.web.commands.crawl.task.operations.image.impl;

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
 * Configuration for {@link FeaturedImageResolver}.
 * </p>
 * @since 2.8.0
 */
@Data
@Accessors(chain = true)
public class FeaturedImageResolverConfig {

    //TODO consider taking advantage of DocImageHandlerConfig since there
    // are overlaps

    public static final String FEATURED_IMAGE_URL_FIELD =
            CrawlDocMetadata.PREFIX + "featured-image-url";
    public static final String FEATURED_IMAGE_PATH_FIELD =
            CrawlDocMetadata.PREFIX + "featured-image-path";
    public static final String FEATURED_IMAGE_INLINE_FIELD =
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

    /**
     * Type of featured image storages.
     */
    public enum Storage {
        /**
         * Default storages. The absolute image URL is stored in a
         * {@value #FEATURED_IMAGE_URL_FIELD} metadata field.
         * When only this storages option is set, scaling options and image
         * format have no effect.
         */
        URL,
        /**
         * Stores a Base64 string of the scaled image, in the format
         * specified, in a {@value #FEATURED_IMAGE_INLINE_FIELD} metadata
         * field. The string is ready to be used inline, in a
         * &lt;img src="..."&gt; tag (as an example).
         */
        INLINE,
        /**
         * Stores the scaled image on the file system, in the format
         * and directory specified. A reference to the file on disk is stored
         * in a {@value #FEATURED_IMAGE_PATH_FIELD} metadata field.
         */
        DISK
    }

    /**
     * Directory structure when storing images on disk.
     */
    public enum StorageDiskStructure {
        /**
         * Create directories for each URL segments, with handling
         * of special characters.
         */
        URL2PATH,
        /**
         * Create directories for each date (e.g., <code>2000/12/31/</code>).
         */
        DATE,
        /**
         * Create directories for each date and time, up to seconds
         * (e.g., <code>2000/12/31/13/34/12/</code>).
         */
        DATETIME
    }

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

    /**
     * Optional regex to overwrite default matching of HTML pages.
     * Default is {@value #DEFAULT_PAGE_CONTENT_TYPE_PATTERN}
     */
    private String pageContentTypePattern = DEFAULT_PAGE_CONTENT_TYPE_PATTERN;
    /**
     * Optional CSS-like path matching one or more image elements.
     */
    private String domSelector;
    /**
     * Minimum pixel size for an image to be considered. Default is 400x400.
     */
    private Dimension minDimensions = DEFAULT_MIN_SIZE;
    /**
     * Target pixel size the featured image should be scaled to.
     * Default is 150x150.
     */
    private Dimension scaleDimensions = DEFAULT_SCALE_SIZE;
    /**
     * Whether to stretch to match scale size. Default keeps aspect ratio.
     */
    private boolean scaleStretch;
    /**
     * Target format of stored image. E.g., "jpg", "png", "gif", "bmp", ...
     * Default is {@value #DEFAULT_IMAGE_FORMAT}
     */
    private String imageFormat = DEFAULT_IMAGE_FORMAT;
    /**
     * Maximum number of images to cache on the local file system for faster
     * processing.
     * Set to 0 to disable caching. Default is
     * {@value #DEFAULT_IMAGE_CACHE_SIZE}.
     */
    private int imageCacheSize = DEFAULT_IMAGE_CACHE_SIZE;

    /**
     * Directory where to cache the images. Defaults to
     * {@value #DEFAULT_IMAGE_CACHE_DIR}
     */
    private Path imageCacheDir;
    /**
     * When more than one featured image is found, whether to return the
     * largest of them all (as opposed to the first one encountered).
     */
    private boolean largest;
    /**
     * One or more type of physical storages for the image.
     */
    private final List<Storage> storages =
            new ArrayList<>(Arrays.asList(DEFAULT_STORAGE));

    /**
     * Path to directory where to store images on disk. Only applicable
     * when one of the values of {@link #getStorages()} is {@link Storage#DISK}.
     */
    private Path storageDiskDir;
    /**
     * The type of directory structure to create when one of the
     * values of of {@link #getStorages()} is {@link Storage#DISK}.
     */
    private StorageDiskStructure storageDiskStructure;
    /**
     * Desired scaling quality. Default is {@link Quality#AUTO}, which tries
     * the best balance between quality and speed based on image size. The
     * lower the quality the faster it is to scale images.
     */
    private Quality scaleQuality = Quality.AUTO;

    /**
     * Name of metadata field where to store the local path to an image.
     * Only applicable if one of the {@link #getStorages()} values
     * is {@link Storage#DISK}.
     * Default is {@value #FEATURED_IMAGE_PATH_FIELD}
     */
    private String storageDiskField = FEATURED_IMAGE_PATH_FIELD;
    /**
     * Name of metadata field where to store the Base64 image.
     * Only applicable if one of the {@link #getStorages()} values
     * is {@link Storage#INLINE}.
     * Default is {@value #FEATURED_IMAGE_INLINE_FIELD}
     */
    private String storageInlineField = FEATURED_IMAGE_INLINE_FIELD;
    /**
     * Name of metadata field where to store the remote image URL.
     * Only applicable if one of the {@link #getStorages()} values
     * is {@link Storage#URL}.
     * Default is {@value #FEATURED_IMAGE_URL_FIELD}
     */
    private String storageUrlField = FEATURED_IMAGE_URL_FIELD;

    /**
     * Gets the storages mechanisms.
     * @return storages mechanisms
     */
    public List<Storage> getStorages() {
        return Collections.unmodifiableList(storages);
    }

    /**
     * Sets the storages mechanisms.
     * @param storages storages mechanisms
     * @return this
     */
    public FeaturedImageResolverConfig setStorages(List<Storage> storages) {
        CollectionUtil.setAll(this.storages, storages);
        return this;
    }
}
