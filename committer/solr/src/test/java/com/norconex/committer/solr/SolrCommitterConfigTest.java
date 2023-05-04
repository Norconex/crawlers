/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.committer.solr;

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

/**
 * SolrCommitter configuration tests.
 *
 * @author Pascal Essiembre
 */
class SolrCommitterConfigTest {

    @Test
    void testWriteRead() {
        SolrCommitter c = new SolrCommitter();

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

        c.setSourceIdField("sourceId");
        c.setTargetIdField("targetId");
        c.setTargetContentField("targetContent");

        c.setSolrClientType(SolrClientType.CONCURRENT_UPDATE_HTTP2);

        c.setSolrCommitDisabled(true);

        c.setSolrURL("http://solrurl.com/test");

        c.setUpdateUrlParam("param1", "value1a");
        c.setUpdateUrlParam("param1", "value1b");
        c.setUpdateUrlParam("param2", "value2");

        XML.assertWriteRead(c, "committer");
    }

    @Test
    void testValidation() {
        Assertions.assertDoesNotThrow(() -> {
            try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
                    ClassUtils.getShortClassName(getClass()) + ".xml"))) {
                XML xml = XML.of(r).create();
                xml.toObjectImpl(SolrCommitter.class);
            }
        });
    }
}
