/* Copyright 2017-2024 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.web.stubs.CrawlerConfigStubs;

class WebCrawlerConfigTest {

    @TempDir
    private Path tempDir;

    @Test
    void testWebCrawlerConfig() {
        assertThatNoException()
                .isThrownBy(
                        () -> BeanMapper.DEFAULT.assertWriteRead(
                                CrawlerConfigStubs
                                        .randomMemoryCrawlerConfig(tempDir)));
    }

    @Test
    void testValidation() {
        assertThatNoException().isThrownBy(() -> {
            try (Reader r = new InputStreamReader(
                    getClass().getResourceAsStream(
                            "/validation/web-crawl-session-large.xml"))) {
                var cfg = new WebCrawlerConfig();
                BeanMapper.DEFAULT.read(cfg, r, Format.XML);
                BeanMapper.DEFAULT.assertWriteRead(cfg);
            } catch (Exception e) {
                System.err.println(ExceptionUtil.getFormattedMessages(e));
                throw e;
            }
        });
    }
}
