/* Copyright 2019-2020 Norconex Inc.
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

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.http.HttpHeaders;
import org.junit.jupiter.api.Assertions;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractInfiniteDepthTestFeature;
import com.norconex.committer.core3.UpsertRequest;
import com.norconex.committer.core3.impl.MemoryCommitter;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.DocMetadata;

/**
 * Test that metadata is extracted properly.
 * @author Pascal Essiembre
 */
public class ValidMetadata extends AbstractInfiniteDepthTestFeature {

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig crawlerConfig)
            throws Exception {
        crawlerConfig.setMaxDepth(10);
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        List<UpsertRequest> docs = committer.getUpsertRequests();

        for (UpsertRequest doc : docs) {
            Properties meta = doc.getMetadata();

            //Test single value
            assertOneValue(meta,
                    HttpHeaders.CONTENT_TYPE,
                    DocMetadata.CONTENT_TYPE,
                    DocMetadata.CONTENT_ENCODING);

            //Test actual values
            Assertions.assertTrue(
                    "text/html; charset=UTF-8".equalsIgnoreCase(
                            meta.getString(HttpHeaders.CONTENT_TYPE)),
                    "Bad HTTP content-type");
            Assertions.assertTrue(
                    "text/html".equalsIgnoreCase(
                            meta.getString(DocMetadata.CONTENT_TYPE)),
                    "Bad Collection content-type.");
            Assertions.assertTrue(
                    StandardCharsets.UTF_8.toString().equalsIgnoreCase(
                        meta.getString(DocMetadata.CONTENT_ENCODING)),
                    "Bad char-encoding.");
        }
    }
}