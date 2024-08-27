/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.importer.handler.transformer.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.importer.TestUtil;

class ImageTransformerTest {

    @Test
    void testWriteRead() {
        var t = new ImageTransformer();
        t.getConfiguration()
                .getCrop()
                .setX(10)
                .setY(15)
                .setWidth(400)
                .setHeight(250);
        t.getConfiguration()
                .getScale()
                .setStretch(true)
                .setWidth(800)
                .setHeight(600)
                .setFactor(0.5);
        t.getConfiguration()
                .setRotation(-90.0)
                .setTargetFormat("jpg");
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(t));
    }

    @Test
    void testImageTransformer() throws IOException {
        var t = new ImageTransformer();
        t.getConfiguration().setRotation(90d);
        var doc = TestUtil.newDocContext("img.png",
                getClass().getResourceAsStream("/parser/image/importer.png"));
        assertThatNoException().isThrownBy(() -> t.accept(doc));
        assertThat(((CachedInputStream) doc.input().asInputStream()).length())
                .isPositive();
    }

    @Test
    void testOperationsDoNotThrow() throws IOException {
        var t = new ImageTransformer();
        var cfg = t.getConfiguration();
        cfg.getScale()
                .setFactor(1.5)
                .setHeight(32)
                .setWidth(64)
                .setStretch(true);
        cfg.setRotation(null);
        cfg.getCrop()
                .setHeight(10)
                .setWidth(20)
                .setX(2)
                .setY(4);

        var doc = TestUtil.newDocContext("img.png",
                getClass().getResourceAsStream("/parser/image/importer.png"));
        assertThatNoException().isThrownBy(() -> t.accept(doc));
        assertThat(((CachedInputStream) doc.input().asInputStream()).length())
                .isPositive();
    }
}
