/* Copyright 2017-2022 Norconex Inc.
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
package com.norconex.crawler.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ClassUtils;

import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.cli.CliLauncher;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;

import lombok.Data;

public final class TestUtil {

    @Data
    public static class Exit {
        private int code;
        private String stdOutErr;
        private final List<String> events = new ArrayList<>();
        public boolean ok() {
            return code == 0;
        }
    }

    private TestUtil() {
    }

    public static Exit testLaunch(String... cmdArgs) throws IOException {
        var exit = new Exit();
        var crawlSessionConfig = new CrawlSessionConfig();
        crawlSessionConfig.addEventListener(
                event -> exit.getEvents().add(event.getName()));
        exit.setStdOutErr(SystemUtil.captureStdoutStderr(() ->
                exit.setCode(CliLauncher.launch(
                        CrawlSession.builder()
                            .crawlerFactory((sess, cfg) -> Crawler.builder()
                                .crawlSession(sess)
                                .crawlerConfig(cfg)
                                .crawlerImpl(Stubber.mockCrawlerImpl(cfg))
                                .build())
                            .crawlSessionConfig(crawlSessionConfig),
                        cmdArgs))
        ));
        return exit;
    }

    public static void testValidation(String xmlResource) throws IOException {
        testValidation(TestUtil.class.getResourceAsStream(xmlResource));

    }
    public static void validate(Class<?> clazz) throws IOException {
        testValidation(clazz, ClassUtils.getShortClassName(clazz) + ".xml");
    }
    public static void testValidation(Class<?> clazz, String xmlResource)
            throws IOException {
        testValidation(clazz.getResourceAsStream(xmlResource));

    }
    public static void testValidation(
            InputStream xmlStream) throws IOException {

        try (Reader r = new InputStreamReader(xmlStream)) {
            XML.of(r).create().validate();
        }
    }
}
