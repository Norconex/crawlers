/* Copyright 2021 Norconex Inc.
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

import org.junit.jupiter.api.Assertions;

import com.norconex.collector.core.filter.impl.ReferenceFilter;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core3.impl.MemoryCommitter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.tagger.impl.ConstantTagger;

/**
 * Tests that a page will force fetching https when HSTS support is
 * in place for a site.
 * @author Pascal Essiembre
 */
//Related to https://github.com/Norconex/collector-http/issues/694
public class StrictTransportSecurity extends AbstractTestFeature {

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {
        cfg.setNumThreads(1);
        cfg.setMaxDocuments(2);
        cfg.setStartURLs(
                // secure
                "https://en.wikipedia.org/wiki/HTTP_Strict_Transport_Security"
        );

        cfg.setReferenceFilters(new ReferenceFilter(TextMatcher.regex(
                ".*en\\.wikipedia\\.org.*"
                + "(Downgrade_attack|HTTP_Strict_Transport_Security)$")));

        ConstantTagger t = new ConstantTagger();
        t.addConstant(
                "secondURL",
                // non-secure
                "http://en.wikipedia.org/wiki/Downgrade_attack");
        cfg.getImporterConfig().setPreParseConsumer(
                HandlerConsumer.fromHandlers(t));
        cfg.setPostImportLinks(TextMatcher.basic("secondURL"));
    }

    @Override
    protected void doHtmlService(PrintWriter out) throws Exception {
        out.println("This page is not used.");
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        Assertions.assertEquals(2, committer.getUpsertCount());
        Assertions.assertEquals(
                "https://en.wikipedia.org/wiki/HTTP_Strict_Transport_Security",
                committer.getUpsertRequests().get(0).getReference());
        Assertions.assertEquals(
                "https://en.wikipedia.org/wiki/Downgrade_attack",
                committer.getUpsertRequests().get(1).getReference());

        Assertions.assertEquals(
                "http://en.wikipedia.org/wiki/Downgrade_attack",
                committer.getUpsertRequests().get(1).getMetadata().getString(
                        HttpDocMetadata.ORIGINAL_REFERENCE));
    }
}
