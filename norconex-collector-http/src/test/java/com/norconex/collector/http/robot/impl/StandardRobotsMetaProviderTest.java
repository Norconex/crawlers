/* Copyright 2010-2020 Norconex Inc.
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
package com.norconex.collector.http.robot.impl;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.junit.Test;

import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.robot.RobotsMeta;
import com.norconex.commons.lang.file.ContentType;

/**
 * @author Pascal Essiembre
 */
public class StandardRobotsMetaProviderTest {


    @Test
    public void testNiceRobotsMeta() throws IOException {
        testRobotsMeta("nice");
    }
    @Test
    public void testUglyRobotsMeta() throws IOException {
        testRobotsMeta("ugly");
    }

    private void testRobotsMeta(String suffix) throws IOException {
        Reader docReader = new InputStreamReader(getClass().getResourceAsStream(
                "StandardRobotsMetaProviderTest-" + suffix + ".html"));
        String docURL = "http://www.example.com/test" + suffix + ".html";
        HttpMetadata metadata = new HttpMetadata(docURL);
        metadata.setString(HttpMetadata.HTTP_CONTENT_TYPE, "text/html");

        StandardRobotsMetaProvider p = new StandardRobotsMetaProvider();
        RobotsMeta robotsMeta = p.getRobotsMeta(
                docReader, docURL, ContentType.HTML, metadata);

        assertTrue("Robots meta should be noindex nofollow.",
                robotsMeta.isNofollow() && robotsMeta.isNoindex());
    }
}
