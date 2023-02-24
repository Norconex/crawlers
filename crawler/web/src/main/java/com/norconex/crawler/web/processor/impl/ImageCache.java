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

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.collections4.map.LRUMap;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.core.store.DataStoreException;

/**
 * Caches images. This class should not be instantiated more than once
 * for the same path. It is best to share the instance.
 * @since 2.8.0
 */
//TODO consider using DataStorageEngine instead.
public class ImageCache {

    private static final Logger LOG = LoggerFactory.getLogger(ImageCache.class);

    private final MVStore store;
    private final Map<String, String> lru;
    private final Path cacheDir;
    private MVMap<String, MVImage> imgCache;

    public ImageCache(int maxSize, Path dir) {
        this.cacheDir = dir;
        try {
            Files.createDirectories(dir);
            LOG.debug("Image cache directory: {}", dir);
        } catch (IOException e) {
            throw new DataStoreException(
                    "Cannot create image cache directory: "
                            + dir.toAbsolutePath(), e);
        }

        this.store = MVStore.open(
                dir.resolve("images").toAbsolutePath().toString());
        this.imgCache = store.openMap("imgCache");
        this.imgCache.clear();

        this.lru = Collections.synchronizedMap(
                new LRUMap<String, String>(maxSize){
            private static final long serialVersionUID = 1L;
            @Override
            protected boolean removeLRU(LinkEntry<String, String> entry) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cache full, removing: {}", entry.getKey());
                }
                imgCache.remove(entry.getKey());
                return super.removeLRU(entry);
            }
        });
        this.store.commit();
    }

    public Path getCacheDirectory() {
        return cacheDir;
    }

    public ScaledImage getImage(String ref) throws IOException {
        lru.put(ref, null);
        MVImage img = imgCache.get(ref);
        if (img == null) {
            return null;
        }
        return new ScaledImage(ref, img.getOriginalDimension(),
                ImageIO.read(new ByteArrayInputStream(img.getImage())));
    }
    public void setImage(ScaledImage scaledImage)
            throws IOException {
        lru.put(scaledImage.getUrl(), null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(scaledImage.getImage(), "png", baos);
        imgCache.put(scaledImage.getUrl(), new MVImage(
                scaledImage.getOriginalSize(), baos.toByteArray()));
        store.commit();
    }

    private static class MVImage implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Dimension originalDimension;
        private final byte[] image;
        public MVImage(Dimension originalDimension, byte[] image) {
            super();
            this.originalDimension = originalDimension;
            this.image = image;
        }
        public Dimension getOriginalDimension() {
            return originalDimension;
        }
        public byte[] getImage() {
            return image;
        }
    }
}
