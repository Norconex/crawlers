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

import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.Reader;

import org.junit.jupiter.api.Test;

import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;

class Neo4jCommitterConfigTest {
    @Test
    void testWriteRead() throws Exception {
        Neo4jCommitter c = new Neo4jCommitter();

        FSQueue q = new FSQueue();
        q.setBatchSize(10);
        q.setMaxPerFolder(5);
        c.setCommitterQueue(q);

        c.setFieldMapping("subject", "title");
        c.setFieldMapping("body", "content");

        c.getRestrictions().add(new PropertyMatcher(
                TextMatcher.basic("document.reference"),
                TextMatcher.wildcard("*.pdf")));
        c.getRestrictions().add(new PropertyMatcher(
                TextMatcher.basic("title"),
                TextMatcher.wildcard("Nah!")));

        Neo4jCommitterConfig cfg = c.getConfig();

        cfg.setUri("someURL");
        cfg.setCredentials(
                new Credentials().setUsername("Nick").setPassword("Fury"));
        cfg.setMultiValuesJoiner("^");
        cfg.setNodeIdProperty("myId");
        cfg.setNodeContentProperty("myContent");
        cfg.setUpsertCypher("my upsert cypher");
        cfg.setDeleteCypher("my delete cypher");

        XML.assertWriteRead(c, "committer");
    }

    @Test
    void testValidation() throws IOException {
        assertThatCode(() -> { 
            try (Reader r = ResourceLoader.getXmlReader(this.getClass())) {
                XML xml = XML.of(r).create();
                xml.toObjectImpl(Neo4jCommitter.class);
            }
        }).doesNotThrowAnyException();
    }
}
