/* Copyright 2017 Norconex Inc.
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

import java.awt.Dimension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;

import com.norconex.collector.core.data.store.CrawlDataStoreException;

public class ImageCache {
    
    private static final Logger LOG = LogManager.getLogger(ImageCache.class);    
    
    private final MVStore store;
    private final Map<String, String> lru;
    private MVMap<String, MVImage> imgCache;
    
    public ImageCache(int maxSize, File dir) {
        
        try {
            FileUtils.forceMkdir(dir);
            LOG.debug("Image cache directory: " + dir);
        } catch (IOException e) {
            throw new CrawlDataStoreException(
                    "Cannot create image cache directory: "
                            + dir.getAbsolutePath(), e);
        }
        
        this.store = MVStore.open(new File(dir, "images").getAbsolutePath());
        this.imgCache = store.openMap("imgCache");
        this.imgCache.clear();            
        
        this.lru = Collections.synchronizedMap(
                new LRUMap<String, String>(maxSize){
            private static final long serialVersionUID = 1L;
            @Override
            protected boolean removeLRU(LinkEntry<String, String> entry) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cache full, removing: " + entry.getKey());
                }
                imgCache.remove(entry.getKey());
                return super.removeLRU(entry);
            }
        });
        this.store.commit();
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
