/* Copyright 2023 Norconex Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.apache.commons.io.IOUtils;

public class TestResource {

    public static final TestResource IMG_160X120_PNG =
            new TestResource("img/160x120.png");
    public static final TestResource IMG_320X240_PNG =
            new TestResource("img/320x240.png");
    public static final TestResource IMG_640X480_PNG =
            new TestResource("img/640x480.png");

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
}
