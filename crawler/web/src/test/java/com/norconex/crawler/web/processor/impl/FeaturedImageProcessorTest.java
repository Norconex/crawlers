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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.img.MutableImage;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.web.MockWebCrawlSession;
import com.norconex.crawler.web.WebStubber;
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
        WebsiteMock.whenPNG(client, "/640x480.png", IMG_640X480_PNG);
        WebsiteMock.whenPNG(client, "/page/320x240.png", IMG_320X240_PNG);
        WebsiteMock.whenPNG(client, "160x120.png", IMG_160X120_PNG);

        var baseUrl = "http://localhost:" + client.getLocalPort();
        var docUrl = baseUrl + "/page/test.html";

        var fetcher = crawler.getFetcher();

        var fip = new FeaturedImageProcessor();
        fip.setStorage(List.of(Storage.INLINE, Storage.URL, Storage.DISK));
        fip.setStorageDiskDir(tempDir.resolve("imageStorage"));
        fip.setImageCacheDir(tempDir.resolve("imageCache"));
        fip.setStorageInlineField("image-inline");
        fip.setStorageUrlField("image-url");
        fip.setStorageDiskField("image-path");
        fip.setLargest(true);
        fip.setImageCacheSize(0);
        fip.setScaleDimensions(null);
        fip.onCrawlerRunBegin(CrawlerEvent.builder()
                .name("test")
                .source(crawler)
                .build());

        // biggest
        var doc = newDoc(docUrl);
        fip.processDocument((HttpFetcher) fetcher, doc);
        var img = new MutableImage(
                Paths.get(doc.getMetadata().getString("image-path")));
        assertThat(doc.getMetadata().getString("image-url")).isEqualTo(
                baseUrl + "/640x480.png");
        assertThat(img.getDimension()).isEqualTo(new Dimension(640, 480));

        // first over 200x200, scaled 50% down
        doc = newDoc(docUrl);
        fip.setLargest(false);
        fip.setMinDimensions(new Dimension(200, 200));
        fip.setScaleDimensions(new Dimension(160, 160));
        fip.processDocument((HttpFetcher) fetcher, doc);
        assertThat(doc.getMetadata().getString("image-url")).isEqualTo(
                baseUrl + "/page/320x240.png");
        img = new MutableImage(
                Paths.get(doc.getMetadata().getString("image-path")));
        assertThat(img.getDimension()).isEqualTo(new Dimension(160, 120));

        // Can fail due to cache... set to memory when testing

        // first over 1x1 (base 64)
        doc = newDoc(docUrl);
        doc.getMetadata().remove("image-url");
        fip.setLargest(false);
        fip.setMinDimensions(new Dimension(1, 1));
        fip.setScaleDimensions(null);
        fip.processDocument((HttpFetcher) fetcher, doc);
        img = new MutableImage(
                Paths.get(doc.getMetadata().getString("image-path")));
        assertThat(img.getDimension()).isEqualTo(new Dimension(5, 5));
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
        p.setStorageDiskDir(Paths.get("c:\\someotherdir"));
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

    private CrawlDoc newDoc(String docUrl) {
        return WebStubber.crawlDoc(
                docUrl,
                ContentType.HTML, ResourceLoader.getHtmlStream(getClass()));
    }
}
