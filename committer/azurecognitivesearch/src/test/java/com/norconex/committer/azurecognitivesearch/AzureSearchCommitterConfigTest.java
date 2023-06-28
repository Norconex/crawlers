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
package com.norconex.committer.azurecognitivesearch;

import java.io.IOException;
import java.io.Reader;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.committer.azurecognitivesearch.AzureSearchCommitter;
import com.norconex.committer.azurecognitivesearch.AzureSearchCommitterConfig;
import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.net.Host;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;

class AzureSearchCommitterConfigTest {

    @Test
    void testWriteRead() throws Exception {
        AzureSearchCommitter c = new AzureSearchCommitter();

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

        AzureSearchCommitterConfig cfg = c.getConfig();

        cfg.setSourceKeyField("sourceKeyField");
        cfg.setTargetKeyField("targetKeyField");
        cfg.setTargetContentField("targetContentField");
        cfg.setEndpoint("endpoint");
        cfg.setApiVersion("apiVersion");
        cfg.setApiKey("apiKey");
        cfg.setIndexName("indexName");
        cfg.setDisableDocKeyEncoding(true);
        cfg.setIgnoreValidationErrors(true);
        cfg.setIgnoreResponseErrors(true);
        cfg.setArrayFields(".*");
        cfg.setArrayFieldsRegex(true);
        cfg.getProxySettings().setHost(new Host("example.com", 1234));

        XML.assertWriteRead(c, "committer");
    }

    @Test
    void testValidation() throws IOException {
        Assertions.assertDoesNotThrow(() -> {
            try (Reader r = ResourceLoader.getXmlReader(this.getClass())) {
                XML xml = XML.of(r).create();
                xml.toObjectImpl(AzureSearchCommitter.class);
            }
        });
    }
}
