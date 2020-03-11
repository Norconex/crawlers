/* Copyright 2020 Norconex Inc.
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
package com.norconex.collector.http.web.feature;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Assertions;

import com.norconex.collector.core.filter.impl.ExtensionReferenceFilter;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.IAddOperation;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.handler.tagger.impl.URLExtractorTagger;

/**
 * Test that links can be specified for crawling after importing.
 * @author Pascal Essiembre
 */
public class PostImportLinks extends AbstractTestFeature {

//    private List<String> queuedURLs = new ArrayList<>();

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig crawlerConfig)
            throws Exception {
        crawlerConfig.setMaxDepth(1);

        // Tell it which field will hold post-import URLs.
        crawlerConfig.setPostImportLinks(TextMatcher.basic("myPostImportURLs"));
        crawlerConfig.setPostImportLinksKeep(true);

        // Keep only the test PDF.
        crawlerConfig.setDocumentFilters(new ExtensionReferenceFilter(
                "pdf", OnMatch.INCLUDE));

        // Create a field with post-import PDF URLs.
        URLExtractorTagger tagger = new URLExtractorTagger();
        tagger.setToField("myPostImportURLs");

        crawlerConfig.getImporterConfig().setPostParseHandlers(
                Arrays.asList(tagger));

    }

    @Override
    protected void doHtmlService(PrintWriter out) throws Exception {
        out.println("<h1>Post import test page.</h1>");
        out.println("URLs in <a href=\"/post-import-links.pdf\">this link</a> "
                + "should be queueued for processing.");
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        assertListSize("additions", committer.getAddOperations(), 1);

        IAddOperation doc = committer.getAddOperations().get(0);

        // Page 2 exists a link value and link label, with different URLs,
        // so we expect 6 links back.
        List<String> expected = Arrays.asList(
                "http://www.example.com/page1.html",
                "http://www.example.com/page2.html",
                "https://www.example.com/page2.html",
                "http://www.example.com/page3.html",
                "https://www.example.com/page4.html",
                "http://www.example.com/page5.html");

        List<String> actual = doc.getMetadata().getStrings("myPostImportURLs");
        assertListSize("extracted links", actual, expected.size());

        Assertions.assertTrue(expected.containsAll(actual),
                "Extracted URLs are not matching expected list: " + actual);
    }
}