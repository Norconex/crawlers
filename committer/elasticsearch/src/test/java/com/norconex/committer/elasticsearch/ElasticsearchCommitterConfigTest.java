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
package com.norconex.committer.elasticsearch;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.text.TextMatcher;

class ElasticsearchCommitterConfigTest {

    @Test
    void testWriteRead() throws Exception {
        var c = new ElasticsearchCommitter();
        var cfg = c.getConfiguration();

        var q = new FSQueue();
        q.getConfiguration()
                .setBatchSize(10)
                .setMaxPerFolder(5);
        cfg.setQueue(q);

        var creds = new Credentials();
        creds.setPassword("mypassword");
        creds.setUsername("myusername");
        cfg.setCredentials(creds);

        cfg.setFieldMapping("subject", "title");
        cfg.setFieldMapping("body", "content");

        cfg.getRestrictions().add(
                new PropertyMatcher(
                        TextMatcher.basic("document.reference"),
                        TextMatcher.wildcard("*.pdf")
                )
        );
        cfg.getRestrictions().add(
                new PropertyMatcher(
                        TextMatcher.basic("title"),
                        TextMatcher.wildcard("Nah!")
                )
        );

        cfg.setSourceIdField("mySourceIdField");
        cfg.setTargetContentField("myTargetContentField");

        cfg.setIndexName("my-inxed");
        cfg.setNodes(List.of("http://localhost:9200", "http://somewhere.com"));
        cfg.setDiscoverNodes(true);
        cfg.setDotReplacement("_");
        cfg.setIgnoreResponseErrors(true);
        cfg.setJsonFieldsPattern("jsonFieldPattern");
        cfg.setConnectionTimeout(Duration.ofMillis(200));
        cfg.setSocketTimeout(Duration.ofMillis(300));
        cfg.setFixBadIds(true);

        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(c)
        );
    }

    @Test
    void testValidation() {
        Assertions.assertDoesNotThrow(() -> {
            BeanMapper.DEFAULT.read(
                    ElasticsearchCommitter.class,
                    ResourceLoader.getXmlReader(getClass()),
                    Format.XML
            );
        });
    }

    @Test
    void testMisc(@TempDir Path tempDir) throws CommitterException {
        Assertions.assertThrows(
                CommitterException.class, () -> {
                    new ElasticsearchCommitter().initBatchCommitter();
                }
        )
                .getMessage().equals("Index name is undefined.");

        @SuppressWarnings("resource")
        var c = new ElasticsearchCommitter();
        var cfg = c.getConfiguration();

        Assertions.assertThrows(
                CommitterException.class,
                () -> c.init(
                        CommitterContext.builder()
                                .setWorkDir(tempDir)
                                .build()
                )
        );

        cfg.setIndexName("index");
        var fsQueue = new FSQueue();
        fsQueue.getConfiguration().setBatchSize(1);
        cfg.setQueue(fsQueue);
        cfg.setDiscoverNodes(true);
        c.init(
                CommitterContext.builder()
                        .setWorkDir(tempDir)
                        .build()
        );

        var reqWithIdTooLong = new UpsertRequest(
                StringUtils.repeat("A", 1024),
                new Properties(), InputStream.nullInputStream()
        );
        var reqOK = new UpsertRequest(
                "AAA", new Properties(), InputStream.nullInputStream()
        );

        cfg.setFixBadIds(false);
        Assertions.assertThrows(CommitterException.class, () -> { //NOSONAR
            c.upsert(reqWithIdTooLong);
            c.upsert(reqOK);
        });
    }
}
