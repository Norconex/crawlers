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
import java.io.IOException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Test;

import com.norconex.collector.http.processor.impl.FeaturedImageProcessor.Quality;
import com.norconex.collector.http.processor.impl.FeaturedImageProcessor.Storage;
import com.norconex.collector.http.processor.impl.FeaturedImageProcessor.StorageDiskStructure;
import com.norconex.commons.lang.config.XMLConfigurationUtil;

public class FeaturedImageProcessorTest {
    
    private static final Logger LOG = LogManager.getLogger(
            FeaturedImageProcessorTest.class);
    
    @Test
    public void testWriteRead() throws IOException {
        FeaturedImageProcessor p = new FeaturedImageProcessor();

        // All settings
        p.setDomSelector("dom.dom");
        p.setImageCacheDir("c:\\somedir");
        p.setImageCacheSize(5000);
        p.setImageFormat("jpg");
        p.setLargest(true);
        p.setMinDimensions(new Dimension(100, 400));
        p.setPageContentTypePattern("text/html");
        p.setScaleQuality(Quality.LOW);
        p.setScaleDimensions(new Dimension(50, 50));
        p.setScaleStretch(true);
        p.setStorage(Storage.URL, Storage.INLINE, Storage.DISK);
        p.setStorageDiskDir("c:\\someotherdir");
        p.setStorageDiskStructure(StorageDiskStructure.DATETIME);
        p.setStorageDiskField("diskField");
        p.setStorageInlineField("inlineField");
        p.setStorageUrlField("urlField");

        LOG.info("Writing/Reading this: " + p);
        XMLConfigurationUtil.assertWriteRead(p);
        
        // Mostly empty
        p.setDomSelector(null);
        p.setImageCacheDir(null);
        p.setImageCacheSize(0);
        p.setImageFormat(null);
        p.setLargest(false);
        p.setMinDimensions(null);
        p.setPageContentTypePattern(null);
        p.setScaleQuality(null);
        p.setScaleDimensions(null);
        p.setScaleStretch(false);
        p.setStorage((Storage) null);
        p.setStorageDiskDir(null);
        p.setStorageDiskStructure(null);
        p.setStorageDiskField(null);
        p.setStorageInlineField(null);
        p.setStorageUrlField(null);

        LOG.info("Writing/Reading this: " + p);
        XMLConfigurationUtil.assertWriteRead(p);
        
    }
}
