/* Copyright 2023 Norconex Inc.
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
package com.norconex.committer.apachekafka;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;

class ApacheKafkaCommitterConfigTest {

    @Test
    void testWriteRead() throws Exception {
        var c = new ApacheKafkaCommitter();

        c.setBootstrapServers("host1:1234, host2:1234");
        c.setTopicName("my-topic");
        c.setCreateTopic(true);
        c.setNumOfPartitions(1);
        c.setReplicationFactor((short) 1);
        
        var q = new FSQueue();
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

        Assertions.assertDoesNotThrow(
                () -> XML.assertWriteRead(c, "committer"));
    }

    @Test
    void testValidation() {
        Assertions.assertDoesNotThrow(() -> {
            try (var r = ResourceLoader.getXmlReader(getClass())) {
                var xml = XML.of(r).create();
                xml.toObjectImpl(ApacheKafkaCommitter.class);
            }
        });
    }
}
