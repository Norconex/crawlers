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
package com.norconex.crawler.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.web.operations.image.impl.FeaturedImage;

public class TestResource {

    public static final TestResource IMG_160X120_PNG =
            new TestResource("img/160x120.png");
    public static final TestResource IMG_320X240_PNG =
            new TestResource("img/320x240.png");
    public static final TestResource IMG_640X480_PNG =
            new TestResource("img/640x480.png");
    public static final TestResource PDF_WITH_LINKS =
            new TestResource("pdf/pdf-with-links.pdf");
    private final String path;

    public TestResource(String path) {
        this.path = path;
    }

    public String asString() {
        return new String(asBytes(), UTF_8);
    }

    public byte[] asBytes() {
        try {
            return IOUtils.toByteArray(asInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public InputStream asInputStream() {
        return TestResource.class.getClassLoader().getResourceAsStream(path);
    }

    public String relativePath() {
        return path;
    }

    public String absolutePath(String baseUrl) {
        return StringUtils.appendIfMissing(baseUrl, "/") + path;
    }

    public BufferedImage asImage() {
        if (!path.startsWith("img/")) {
            throw new UnsupportedOperationException(
                    "Path indicates this not an image: " + path);
        }
        try {
            return ImageIO.read(asInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public FeaturedImage asFeaturedImage(String baseUrl) {
        var img = asImage();
        return new FeaturedImage(
                absolutePath(baseUrl),
                new Dimension(img.getWidth(), img.getHeight()),
                img);
    }
}
