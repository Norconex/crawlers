/* Copyright 2017-2023 Norconex Inc.
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
package com.norconex.committer.elasticsearch;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;

class ElasticsearchCommitterConfigTest {

    @Test
    void testWriteRead() throws Exception {
        var c = new ElasticsearchCommitter();

        var q = new FSQueue();
        q.setBatchSize(10);
        q.setMaxPerFolder(5);
        c.setCommitterQueue(q);

        var creds = new Credentials();
        creds.setPassword("mypassword");
        creds.setUsername("myusername");
        c.setCredentials(creds);

        c.setFieldMapping("subject", "title");
        c.setFieldMapping("body", "content");

        c.getRestrictions().add(new PropertyMatcher(
                TextMatcher.basic("document.reference"),
                TextMatcher.wildcard("*.pdf")));
        c.getRestrictions().add(new PropertyMatcher(
                TextMatcher.basic("title"),
                TextMatcher.wildcard("Nah!")));

        c.setSourceIdField("mySourceIdField");
        c.setTargetContentField("myTargetContentField");

        c.setIndexName("my-inxed");
        c.setNodes("http://localhost:9200", "http://somewhere.com");
        c.setDiscoverNodes(true);
        c.setDotReplacement("_");
        c.setIgnoreResponseErrors(true);
        c.setJsonFieldsPattern("jsonFieldPattern");
        c.setConnectionTimeout(Duration.ofMillis(200));
        c.setSocketTimeout(Duration.ofMillis(300));
        c.setFixBadIds(true);

        Assertions.assertDoesNotThrow(
                () -> XML.assertWriteRead(c, "committer"));
    }

    @Test
    void testValidation() {
        Assertions.assertDoesNotThrow(() -> {
            try (var r = ResourceLoader.getXmlReader(getClass())) {
                var xml = XML.of(r).create();
                xml.toObjectImpl(ElasticsearchCommitter.class);
            }
        });
    }

    @Test
    void testMisc(@TempDir Path tempDir) throws CommitterException {
        Assertions.assertThrows(CommitterException.class, () -> {
            new ElasticsearchCommitter().initBatchCommitter();
        });

        @SuppressWarnings("resource")
        var c = new ElasticsearchCommitter();

        Assertions.assertThrows(CommitterException.class, () ->
            c.init(CommitterContext.builder()
                    .setWorkDir(tempDir)
                    .build()));

        c.setIndexName("index");
        var fsQueue = new FSQueue();
        fsQueue.setBatchSize(1);
        c.setCommitterQueue(fsQueue);
        c.setDiscoverNodes(true);
        c.init(CommitterContext.builder()
                .setWorkDir(tempDir)
                .build());

        var reqWithIdTooLong = new UpsertRequest(
                StringUtils.repeat("A", 1024),
                new Properties(), InputStream.nullInputStream());
        var reqOK = new UpsertRequest(
                "AAA", new Properties(), InputStream.nullInputStream());

        c.setFixBadIds(false);
        Assertions.assertThrows(CommitterException.class, () -> { //NOSONAR
            c.upsert(reqWithIdTooLong);
            c.upsert(reqOK);
        });
    }
}
