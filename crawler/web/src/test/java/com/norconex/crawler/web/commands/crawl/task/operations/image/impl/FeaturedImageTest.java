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
package com.norconex.crawler.web.commands.crawl.task.operations.image.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.awt.Dimension;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.web.TestResource;
import com.norconex.crawler.web.commands.crawl.task.operations.image.impl.FeaturedImage;

class FeaturedImageTest {

    @Test
    void testFeaturedImage() throws IOException {

        var img160x120 = TestResource.IMG_160X120_PNG.asFeaturedImage(
                "http://somewhere.com/");
        var img320x240 = TestResource.IMG_320X240_PNG.asFeaturedImage(
                "http://somewhere.com/");
        var img640x480 = TestResource.IMG_640X480_PNG.asFeaturedImage(
                "http://somewhere.com/");

        assertThat(img320x240.fits((Dimension) null)).isFalse();
        assertThat(img320x240.fits(new Dimension(330, 260))).isTrue();
        assertThat(img320x240.fits(new Dimension(320, 240))).isTrue();
        assertThat(img320x240.fits(new Dimension(320, 200))).isFalse();
        assertThat(img320x240.fits(new Dimension(200, 200))).isFalse();

        assertThat(img320x240.fits((FeaturedImage) null)).isFalse();
        assertThat(img320x240.fits(img160x120)).isFalse();
        assertThat(img320x240.fits(img320x240)).isTrue();
        assertThat(img320x240.fits(img640x480)).isTrue();

        assertThat(img320x240.contains((Dimension) null)).isFalse();
        assertThat(img320x240.contains(new Dimension(330, 260))).isFalse();
        assertThat(img320x240.contains(new Dimension(320, 240))).isTrue();
        assertThat(img320x240.contains(new Dimension(320, 200))).isTrue();
        assertThat(img320x240.contains(new Dimension(200, 200))).isTrue();

        assertThat(img320x240.contains((FeaturedImage) null)).isFalse();
        assertThat(img320x240.contains(img160x120)).isTrue();
        assertThat(img320x240.contains(img320x240)).isTrue();
        assertThat(img320x240.contains(img640x480)).isFalse();

        assertThat(img160x120.getArea()).isEqualTo(19_200);
        assertThatNoException().isThrownBy(
                () -> img160x120.toHTMLInlineString("png"));
        assertThat(img160x120.getUrl()).isEqualTo(
                "http://somewhere.com/img/160x120.png");
    }

}
