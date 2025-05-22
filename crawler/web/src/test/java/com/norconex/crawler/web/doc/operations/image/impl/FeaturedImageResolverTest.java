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

import static com.norconex.crawler.web.TestResource.IMG_160X120_PNG;
import static com.norconex.crawler.web.TestResource.IMG_320X240_PNG;
import static com.norconex.crawler.web.TestResource.IMG_640X480_PNG;
import static com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageResolverConfig.Storage.DISK;
import static com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageResolverConfig.Storage.INLINE;
import static com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageResolverConfig.Storage.URL;
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
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.img.MutableImage;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageResolverConfig.Quality;
import com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageResolverConfig.Storage;
import com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageResolverConfig.StorageDiskStructure;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.mocks.MockWebsite;
import com.norconex.crawler.web.stubs.CrawlDocStubs;

@MockServerSettings
class FeaturedImageResolverTest {

    private @TempDir Path tempDir;

    @WebCrawlTest(focus = Focus.CONTEXT)
    void testProcessFeaturedImage(
            ClientAndServer client, CrawlContext ctx)
            throws IOException {
        MockWebsite.whenPNG(client, "/640x480.png", IMG_640X480_PNG);
        MockWebsite.whenPNG(client, "/page/320x240.png", IMG_320X240_PNG);
        MockWebsite.whenPNG(client, "160x120.png", IMG_160X120_PNG);

        var baseUrl = "http://localhost:" + client.getLocalPort();
        var docUrl = baseUrl + "/page/test.html";

        var fetcher = ctx.getFetcher();

        var fip = new FeaturedImageResolver();
        fip.getConfiguration()
                .setStorages(List.of(INLINE, URL, DISK))
                .setStorageDiskDir(tempDir.resolve("imageStorage"))
                .setImageCacheDir(tempDir.resolve("imageCache"))
                .setStorageInlineField("image-inline")
                .setStorageUrlField("image-url")
                .setStorageDiskField("image-path")
                .setLargest(true)
                .setImageCacheSize(0)
                .setScaleDimensions(null);
        fip.onCrawlerCrawlBegin(
                CrawlerEvent.builder()
                        .name("test")
                        .source(ctx)
                        .build());

        // biggest
        var doc = newDoc(docUrl);
        fip.accept(fetcher, doc);
        var img = new MutableImage(
                Paths.get(doc.getMetadata().getString("image-path")));
        assertThat(doc.getMetadata().getString("image-url")).isEqualTo(
                baseUrl + "/640x480.png");
        assertThat(img.getDimension()).isEqualTo(new Dimension(640, 480));

        // first over 200x200, scaled 50% down
        doc = newDoc(docUrl);
        fip.getConfiguration()
                .setLargest(false)
                .setMinDimensions(new Dimension(200, 200))
                .setScaleDimensions(new Dimension(160, 160));
        fip.accept(fetcher, doc);
        assertThat(doc.getMetadata().getString("image-url")).isEqualTo(
                baseUrl + "/page/320x240.png");
        img = new MutableImage(
                Paths.get(doc.getMetadata().getString("image-path")));
        assertThat(img.getDimension()).isEqualTo(new Dimension(160, 120));

        // Can fail due to cache... set to memory when testing

        // first over 1x1 (base 64)
        doc = newDoc(docUrl);
        doc.getMetadata().remove("image-url");
        fip.getConfiguration()
                .setLargest(false)
                .setMinDimensions(new Dimension(1, 1))
                .setScaleDimensions(null);
        fip.accept(fetcher, doc);
        img = new MutableImage(
                Paths.get(doc.getMetadata().getString("image-path")));
        assertThat(img.getDimension()).isEqualTo(new Dimension(5, 5));
    }

    @Test
    void testWriteRead() {
        var p = new FeaturedImageResolver();

        // All settings
        p.getConfiguration()
                .setDomSelector("dom.dom")
                .setImageCacheDir(Paths.get("c:\\somedir"))
                .setImageCacheSize(5000)
                .setImageFormat("jpg")
                .setLargest(true)
                .setMinDimensions(new Dimension(100, 400))
                .setPageContentTypePattern("text/html")
                .setScaleQuality(Quality.LOW)
                .setScaleDimensions(new Dimension(50, 50))
                .setScaleStretch(true)
                .setStorages(List.of(Storage.URL, Storage.INLINE, Storage.DISK))
                .setStorageDiskDir(Paths.get("c:\\someotherdir"))
                .setStorageDiskStructure(StorageDiskStructure.DATETIME)
                .setStorageDiskField("diskField")
                .setStorageInlineField("inlineField")
                .setStorageUrlField("urlField");

        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(p));

        //TODO migrate this:

        //        // Mostly empty
        //        p.setDomSelector(null);
        //        p.setImageCacheDir(null);
        //        p.setImageCacheSize(0);
        //        p.setImageFormat(null);
        //        p.setLargest(false);
        //        p.setMinDimensions(null);
        //        p.setPageContentTypePattern(null);
        //        p.setScaleQuality(null);
        //        p.setScaleDimensions(null);
        //        p.setScaleStretch(false);
        //        p.setStorage((List<Storage>) null);
        //        p.setStorageDiskDir(null);
        //        p.setStorageDiskStructure(null);
        //        p.setStorageDiskField(null);
        //        p.setStorageInlineField(null);
        //        p.setStorageUrlField(null);
        //
        //        // should come back with default values set.
        //        // TODO consider having default resolved at runtime instead, and
        //        // set everything null by default?
        //
        //        var read = BeanMapper.DEFAULT.writeRead(p, Format.XML);
        //        assertThat(read).isEqualTo(new FeaturedImageResolver());
        //
        ////        assertThatNoException().isThrownBy(
        ////                () -> BeanMapper.DEFAULT.assertWriteRead(p));
    }

    private CrawlDoc newDoc(String docUrl) {
        return CrawlDocStubs.crawlDoc(
                docUrl,
                ContentType.HTML, ResourceLoader.getHtmlStream(getClass()));
    }
}
