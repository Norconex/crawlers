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
package com.norconex.cfgconverter;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.apache.commons.io.IOUtils;

final class TestUtil {
    private TestUtil() {}

    public static String v3ImporterXmlString() {
        return resourceAsString("/v3-importer.xml");
    }

    public static String v4ImporterXmlString() {
        return resourceAsString("/v4-importer.xml");
    }

    public static String v3HttpCollectorXmlString() {
        var importerXml = v3ImporterXmlString();
        var collectorXml = resourceAsString("/v3-http-collector.xml");
        return collectorXml.replace(
                "    <!--v3-importer.xml-->",
                importerXml.replaceAll("^", "    "));
    }

    public static String v4WebCrawlerXmlString() {
        var importerXml = v4ImporterXmlString();
        var crawlerXml = resourceAsString("/v4-web-crawler.xml");
        return crawlerXml.replace(
                "    <!--v4-importer.xml-->",
                importerXml.replaceAll("^", "    "));
    }

    private static String resourceAsString(String resource) {
        try {
            return IOUtils.toString(
                    TestUtil.class.getResourceAsStream(resource), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
