/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.robot.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.DocMetaConstants;

class StandardRobotsMetaProviderTest {

    @Test
    void testNiceRobotsMeta() throws IOException {
        testRobotsMeta("nice");
    }

    @Test
    void testUglyRobotsMeta() throws IOException {
        testRobotsMeta("ugly");
    }

    private void testRobotsMeta(String suffix) throws IOException {
        Reader docReader = new InputStreamReader(
                getClass().getResourceAsStream(
                        "StandardRobotsMetaProviderTest-" + suffix + ".html"));
        var docURL = "http://www.example.com/test" + suffix + ".html";
        var metadata = new Properties();
        metadata.set(DocMetaConstants.REFERENCE, docURL);
        metadata.set(HttpHeaders.CONTENT_TYPE, "text/html");

        var p = new StandardRobotsMetaProvider();
        var robotsMeta = p.getRobotsMeta(
                docReader, docURL, ContentType.HTML, metadata);

        assertTrue(
                robotsMeta.isNofollow() && robotsMeta.isNoindex(),
                "Robots meta should be noindex nofollow.");
    }

    @Test
    void testFindInHeaders() throws IOException {
        var metadata = new Properties();
        metadata.set(DocMetaConstants.REFERENCE, "someRef");
        metadata.set(HttpHeaders.CONTENT_TYPE, "text/html");
        metadata.set("X-Robots-Tag", "noindex, nofollow");

        var p = new StandardRobotsMetaProvider();
        var robotsMeta = p.getRobotsMeta(
                Reader.nullReader(), "someRef", ContentType.HTML, metadata);

        assertTrue(
                robotsMeta.isNofollow() && robotsMeta.isNoindex(),
                "Robots meta should be noindex nofollow.");
    }
}
