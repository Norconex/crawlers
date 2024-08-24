/* Copyright 2020-2024 Norconex Inc.
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
package com.norconex.crawler.web.fetch.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.img.MutableImage;
import com.norconex.crawler.web.TestResource;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.DirStructure;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.Target;
import com.norconex.crawler.web.stubs.CrawlDocStubs;

class DocImageHandlerTest {

    @Test
    void testWriteRead() {
        var h = new DocImageHandler();
        h.getConfiguration()
        .setImageFormat("jpg")
        .setTargetDir(Paths.get("/tmp/blah"))
        .setTargetDirStructure(DirStructure.URL2PATH)
        .setTargetDirField("docImage")
        .setTargetMetaField("docMeta")
        .setTargets(List.of(Target.DIRECTORY, Target.METADATA));

        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(h));
    }

    @Test
    void testHandleImage(@TempDir Path tempDir) throws IOException {
        var h = new DocImageHandler();
        h.getConfiguration()
            .setTargetDir(tempDir)
            .setTargetDirField("img-path")
            .setTargetMetaField("img-64")
            .setImageFormat("jpg")
            .setTargetDirStructure(DirStructure.DATE)
            .setTargets(List.of(Target.DIRECTORY, Target.METADATA));

        var doc = CrawlDocStubs.crawlDoc(
                "http://site.com/page.html",
                ContentType.HTML,
                InputStream.nullInputStream());
        h.handleImage(TestResource.IMG_320X240_PNG.asInputStream(), doc);

        var file = new File(doc.getMetadata().getString("img-path"));
        var img1 = ImageIO.read(file);
        var img2 = MutableImage.fromBase64String(
                doc.getMetadata().getString("img-64")).toImage();

        var baos1 = new ByteArrayOutputStream();
        ImageIO.write(img1, "jpg", baos1);
        var baos2 = new ByteArrayOutputStream();
        ImageIO.write(img2, "jpg", baos2);

        assertThat(baos1.toByteArray()).isEqualTo(baos2.toByteArray());
        assertThat(img1.getWidth()).isEqualTo(320);
        assertThat(img1.getHeight()).isEqualTo(240);
    }
}