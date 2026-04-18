/* Copyright 2017-2025 Norconex Inc.
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.collections4.map.LRUMap;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Thread-safe, in-memory LRU cache for featured images, keyed by
 * image URL. Prevents re-downloading and re-scaling the same image
 * when it appears on multiple crawled pages (e.g., a shared logo
 * or banner).
 * </p><p>
 * Images are stored as <b>PNG-compressed byte arrays</b> rather than
 * raw {@code BufferedImage} objects, reducing per-entry memory by
 * roughly 10&ndash;50&times; (e.g., a 200&times;200 scaled image
 * uses ~5&ndash;10&nbsp;KB compressed vs ~160&nbsp;KB raw).
 * </p><p>
 * The cache is local to each crawler node &mdash; it is intentionally
 * <b>not</b> shared across cluster nodes because serializing image
 * payloads through the grid would negate the performance benefit.
 * </p>
 * @since 2.8.0
 */
@Slf4j
public class ImageCache {

    private record CachedEntry(
            Dimension originalSize, byte[] pngBytes) {
    }

    private final Map<String, CachedEntry> lru;

    /**
     * Creates a new in-memory image cache with the given maximum
     * number of entries.
     * @param maxSize maximum cached images before the
     *                least-recently used entry is evicted
     */
    public ImageCache(int maxSize) {
        lru = Collections.synchronizedMap(
                new LRUMap<String, CachedEntry>(maxSize) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected boolean removeLRU(
                            LinkEntry<String, CachedEntry> entry) {
                        LOG.debug(
                                "Cache full, evicting: {}",
                                entry.getKey());
                        return true;
                    }
                });
    }

    /**
     * Returns the cached image for the given URL, or {@code null}
     * if absent or if the cached entry cannot be decoded.
     * @param url image URL
     * @return cached image or {@code null}
     */
    public FeaturedImage getImage(String url) {
        var entry = lru.get(url);
        if (entry == null) {
            return null;
        }
        try {
            var bi = ImageIO.read(
                    new ByteArrayInputStream(entry.pngBytes()));
            return new FeaturedImage(
                    url, entry.originalSize(), bi);
        } catch (IOException e) {
            LOG.debug("Could not decode cached image: {}", url, e);
            lru.remove(url);
            return null;
        }
    }

    /**
     * Stores a featured image in the cache as a PNG-compressed
     * byte array, keyed by its URL.
     * @param image the featured image to cache
     */
    public void setImage(FeaturedImage image) {
        try {
            var baos = new ByteArrayOutputStream();
            ImageIO.write(image.getImage(), "png", baos);
            lru.put(image.getUrl(), new CachedEntry(
                    image.getOriginalSize(), baos.toByteArray()));
        } catch (IOException e) {
            LOG.debug(
                    "Could not compress image for caching: {}",
                    image.getUrl(), e);
        }
    }

    /**
     * Returns the number of images currently in the cache.
     * @return current cache size
     */
    public int getCacheSize() {
        return lru.size();
    }
}
