/* Copyright 2019 Norconex Inc.
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
package com.norconex.collector.http.web.features;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Assertions;

import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.commons.lang.file.FileUtil;

/**
 * Tests that downloaded files are kept with "keepDownloads" is
 * <code>true</code>.
 * @author Pascal Essiembre
 */
public class KeepDownloads extends AbstractTestFeature {

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {
        cfg.setMaxDepth(0);
        cfg.setKeepDownloads(true);
        cfg.setStartURLs(cfg.getStartURLs().get(0) + "/a$dir/blah?param=fake");
    }

    @Override
    public void doHtmlService(PrintWriter out) throws Exception {
        out.println("<h1>Keep downloaded files == true</h1>");
        out.println("<b>This</b> file <i>must</i> be saved as is, "
                + "with this <span>formatting</span>");
    }

    @Override
    protected void doTestCrawler(HttpCrawler crawler) throws Exception {
        File downloadDir = crawler.getDownloadDir().toFile();

        final Mutable<File> downloadedFile = new MutableObject<>();
        FileUtil.visitAllFiles(downloadDir, file -> {
            if (downloadedFile.getValue() != null) {
                return;
            }
            if (file.toString().contains("downloads")) {
                downloadedFile.setValue(file);
            }
        });
        String content = FileUtils.readFileToString(
                downloadedFile.getValue(), StandardCharsets.UTF_8);
        Assertions.assertTrue(
                content.contains("<b>This</b> file <i>must</i> be saved as is, "
                        + "with this <span>formatting</span>"),
                "Invalid or missing download file.");
    }
}
