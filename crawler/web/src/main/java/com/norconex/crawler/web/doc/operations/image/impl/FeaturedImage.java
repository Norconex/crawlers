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
package com.norconex.crawler.web.doc.operations.image.impl;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import javax.imageio.ImageIO;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
public class FeaturedImage {
    private String url;
    private final Dimension originalSize;
    private final BufferedImage image;
    public FeaturedImage(
            String url, Dimension originalSize, BufferedImage image) {
        this.url = url;
        this.originalSize = originalSize;
        this.image = image;
    }
    public Dimension getOriginalSize() {
        return originalSize;
    }
    public BufferedImage getImage() {
        return image;
    }
    public String getUrl() {
        return url;
    }

    public boolean contains(FeaturedImage img) {
        if (img == null) {
            return false;
        }
        return contains(img.getOriginalSize());
    }
    public boolean contains(Dimension dim) {
        if (dim == null) {
            return false;
        }
        return contains((int) dim.getWidth(), (int) dim.getHeight());
    }
    public boolean contains(int width, int height) {
        return width <= originalSize.getWidth()
                && height <= originalSize.getHeight();
    }
    public boolean fits(FeaturedImage img) {
        if (img == null) {
            return false;
        }
        return fits(img.getOriginalSize());
    }
    public boolean fits(Dimension dim) {
        if (dim == null) {
            return false;
        }
        return fits((int) dim.getWidth(), (int) dim.getHeight());
    }
    public boolean fits(int width, int height) {
        return originalSize.getWidth() <= width
                && originalSize.getHeight() <= height;
    }
    public long getArea() {
        return (long) originalSize.getWidth() * (long) originalSize.getHeight();
    }
    public String toHTMLInlineString(String format) throws IOException {
        var baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return "data:image/" + format + ";base64,"
                + Base64.getMimeEncoder().encodeToString(baos.toByteArray());
    }
}