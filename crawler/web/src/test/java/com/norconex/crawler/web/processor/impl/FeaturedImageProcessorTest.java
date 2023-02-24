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

import static com.norconex.crawler.web.TestResource.IMG_160X120_PNG;
import static com.norconex.crawler.web.TestResource.IMG_320X240_PNG;
import static com.norconex.crawler.web.TestResource.IMG_640X480_PNG;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.web.MockWebCrawlSession;
import com.norconex.crawler.web.Stubber;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.processor.impl.FeaturedImageProcessor.Quality;
import com.norconex.crawler.web.processor.impl.FeaturedImageProcessor.Storage;
import com.norconex.crawler.web.processor.impl.FeaturedImageProcessor.StorageDiskStructure;

@MockServerSettings
class FeaturedImageProcessorTest {

    private @TempDir Path tempDir;

    @Test
    @MockWebCrawlSession
    void testProcessFeaturedImage(
            ClientAndServer client, Crawler crawler)
            throws IOException {
        WebsiteMock.whenImagePng(client, "/640x480.png", IMG_640X480_PNG);
        WebsiteMock.whenImagePng(client, "/320x240.png", IMG_320X240_PNG);
        WebsiteMock.whenImagePng(client, "/160x120.png", IMG_160X120_PNG);

        var fetcher = crawler.getFetcher();

        var fip = new FeaturedImageProcessor();
        fip.setStorage(List.of(Storage.INLINE, Storage.URL, Storage.DISK));
        fip.setStorageDiskDir(tempDir.resolve("imageStorage").toString());
        fip.setImageCacheDir(tempDir.resolve("imageCache"));
        fip.setStorageInlineField("image-inline");
        fip.setStorageUrlField("image-url");
        fip.setLargest(true);

        var doc = Stubber.crawlDoc(
                "http://localhost:" + client.getLocalPort() + "/test.html",
                ContentType.HTML, ResourceLoader.getHtmlStream(getClass()));

        fip.processDocument((HttpFetcher) fetcher, doc);

        System.out.println("FILE: " + doc.getMetadata().getString("image-url"));
    }

    private byte[] image(String fileName) throws IOException {
        return IOUtils.toByteArray(getClass().getClassLoader()
                .getResourceAsStream(fileName));
    }

    @Test
    void testWriteRead() {
        var p = new FeaturedImageProcessor();

        // All settings
        p.setDomSelector("dom.dom");
        p.setImageCacheDir(Paths.get("c:\\somedir"));
        p.setImageCacheSize(5000);
        p.setImageFormat("jpg");
        p.setLargest(true);
        p.setMinDimensions(new Dimension(100, 400));
        p.setPageContentTypePattern("text/html");
        p.setScaleQuality(Quality.LOW);
        p.setScaleDimensions(new Dimension(50, 50));
        p.setScaleStretch(true);
        p.setStorage(List.of(Storage.URL, Storage.INLINE, Storage.DISK));
        p.setStorageDiskDir("c:\\someotherdir");
        p.setStorageDiskStructure(StorageDiskStructure.DATETIME);
        p.setStorageDiskField("diskField");
        p.setStorageInlineField("inlineField");
        p.setStorageUrlField("urlField");

        assertThatNoException().isThrownBy(() ->
                XML.assertWriteRead(p, "processor"));

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
        p.setStorage((List<Storage>) null);
        p.setStorageDiskDir(null);
        p.setStorageDiskStructure(null);
        p.setStorageDiskField(null);
        p.setStorageInlineField(null);
        p.setStorageUrlField(null);

        assertThatNoException().isThrownBy(() ->
                XML.assertWriteRead(p, "processor"));
    }
}
