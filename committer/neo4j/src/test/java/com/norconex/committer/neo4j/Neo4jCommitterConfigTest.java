/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.committer.neo4j;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.text.TextMatcher;

class Neo4jCommitterConfigTest {
    @Test
    void testWriteRead() throws Exception {
        var c = new Neo4jCommitter();

        var q = new FSQueue();
        q.getConfiguration().setBatchSize(10);
        q.getConfiguration().setMaxPerFolder(5);
        c.getConfiguration().setQueue(q);

        c.getConfiguration().setFieldMapping("subject", "title");
        c.getConfiguration().setFieldMapping("body", "content");

        c.getConfiguration().getRestrictions().add(new PropertyMatcher(
                TextMatcher.basic("document.reference"),
                TextMatcher.wildcard("*.pdf")));
        c.getConfiguration().getRestrictions().add(new PropertyMatcher(
                TextMatcher.basic("title"),
                TextMatcher.wildcard("Nah!")));

        var cfg = c.getConfiguration();

        cfg.setUri("someURL");
        cfg.setCredentials(
                new Credentials().setUsername("Nick").setPassword("Fury"));
        cfg.setMultiValuesJoiner("^");
        cfg.setNodeIdProperty("myId");
        cfg.setNodeContentProperty("myContent");
        cfg.setUpsertCypher("my upsert cypher");
        cfg.setDeleteCypher("my delete cypher");

        BeanMapper.DEFAULT.assertWriteRead(c);
    }

    @Test
    void testValidation() throws IOException {
        Assertions.assertDoesNotThrow(() -> {
            try (var r = ResourceLoader.getXmlReader(this.getClass())) {
                BeanMapper.DEFAULT.read(Neo4jCommitter.class, r, Format.XML);
            }
        });
    }
}
