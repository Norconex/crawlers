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

import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;

public class ElasticsearchCommitterConfigTest {

    @Test
    public void testWriteRead() throws Exception {
        ElasticsearchCommitter c = new ElasticsearchCommitter();

        FSQueue q = new FSQueue();
        q.setBatchSize(10);
        q.setMaxPerFolder(5);
        c.setCommitterQueue(q);

        Credentials creds = new Credentials();
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
        c.setConnectionTimeout(200);
        c.setSocketTimeout(300);
        c.setFixBadIds(true);

        XML.assertWriteRead(c, "committer");
    }

    @Test
    void testValidation() {
        Assertions.assertDoesNotThrow(() -> {
            try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
                    ClassUtils.getShortClassName(getClass()) + ".xml"))) {
                XML xml = XML.of(r).create();
                xml.toObjectImpl(ElasticsearchCommitter.class);
            }
        });
    }
}
