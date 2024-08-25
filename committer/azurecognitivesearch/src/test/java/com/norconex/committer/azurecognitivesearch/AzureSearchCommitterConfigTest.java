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

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.net.Host;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.text.TextMatcher;

class AzureSearchCommitterConfigTest {

    @Test
    void testWriteRead() throws Exception {
        var q = new FSQueue();
        q.getConfiguration()
                .setBatchSize(10)
                .setMaxPerFolder(5);

        var c = new AzureSearchCommitter();
        c.getConfiguration()
                .setSourceKeyField("sourceKeyField")
                .setTargetKeyField("targetKeyField")
                .setTargetContentField("targetContentField")
                .setProxySettings(
                        new ProxySettings()
                                .setHost(new Host("there.com", 123))
                )
                .setEndpoint("endpoint")
                .setApiVersion("apiVersion")
                .setApiKey("apiKey")
                .setIndexName("indexName")
                .setDisableDocKeyEncoding(true)
                .setIgnoreValidationErrors(true)
                .setIgnoreResponseErrors(true)
                .setArrayFields(".*")
                .setArrayFieldsRegex(true)
                .setQueue(q)
                .setFieldMapping("subject", "title")
                .setFieldMapping("body", "content")
                .addRestriction(
                        new PropertyMatcher(
                                TextMatcher.basic("document.reference"),
                                TextMatcher.wildcard("*.pdf")
                        )
                )
                .addRestriction(
                        new PropertyMatcher(
                                TextMatcher.basic("title"),
                                TextMatcher.wildcard("Nah!")
                        )
                );

        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(c)
        );
    }

    @Test
    void testValidation() throws IOException {
        Assertions.assertDoesNotThrow(() -> {
            try (var r = ResourceLoader.getXmlReader(this.getClass())) {
                BeanMapper.DEFAULT.read(
                        AzureSearchCommitter.class, r, Format.XML
                );
            }
        });
    }
}
