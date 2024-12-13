/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.cmd.crawl.operations.image.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.web.TestResource;

class ImageCacheTest {

    @TempDir
    private Path tempDir;

    @Test
    void testImageCache() throws IOException {
        var baseUrl = "http://somewhere.com/";
        var ref160 = TestResource.IMG_160X120_PNG.absolutePath(baseUrl);
        var ref320 = TestResource.IMG_320X240_PNG.absolutePath(baseUrl);
        var ref640 = TestResource.IMG_640X480_PNG.absolutePath(baseUrl);

        var cache = new ImageCache(2, tempDir);

        assertThat(cache.getCacheDirectory()).isEqualTo(tempDir);

        cache.setImage(TestResource.IMG_160X120_PNG.asFeaturedImage(baseUrl));
        assertThat(cache.imgCache).hasSize(1);
        assertThat(cache.getImage(ref160)).isNotNull();
        assertThat(cache.getImage(ref320)).isNull();
        assertThat(cache.getImage(ref640)).isNull();

        // one more time the same image should not change anything
        cache.setImage(TestResource.IMG_160X120_PNG.asFeaturedImage(baseUrl));
        assertThat(cache.imgCache).hasSize(1);
        assertThat(cache.getImage(ref160)).isNotNull();
        assertThat(cache.getImage(ref320)).isNull();
        assertThat(cache.getImage(ref640)).isNull();

        cache.setImage(TestResource.IMG_320X240_PNG.asFeaturedImage(baseUrl));
        assertThat(cache.imgCache).hasSize(2);
        assertThat(cache.getImage(ref160)).isNotNull();
        assertThat(cache.getImage(ref320)).isNotNull();
        assertThat(cache.getImage(ref640)).isNull();

        // Since max is 2, size should remain 2
        cache.setImage(TestResource.IMG_640X480_PNG.asFeaturedImage(baseUrl));
        assertThat(cache.imgCache).hasSize(2);
        assertThat(cache.getImage(ref160)).isNull();
        assertThat(cache.getImage(ref320)).isNotNull();
        assertThat(cache.getImage(ref640)).isNotNull();

        cache.getStore().close();
    }
}
