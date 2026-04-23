/* Copyright 2023-2026 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.jetty.ConcurrentUpdateJettySolrClient;
import org.apache.solr.client.solrj.jetty.HttpJettySolrClient;
import org.apache.solr.client.solrj.jetty.LBJettySolrClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@SuppressWarnings("deprecation")
@Timeout(30)
class SolrClientTypeTest {

    @Test
    void testSolrClientType() throws IOException {
        var url = "http://solr/base-url";

        var solr = SolrClientType.HTTP.create(url);
        assertThat(solr).isInstanceOf(HttpJettySolrClient.class);

        solr = SolrClientType.CONCURRENT_UPDATE.create(url);
        assertThat(solr).isInstanceOf(ConcurrentUpdateJettySolrClient.class);

        solr = SolrClientType.LB_HTTP.create(url);
        assertThat(solr).isInstanceOf(LBJettySolrClient.class);

        solr = SolrClientType.HTTP2.create(url);
        assertThat(solr).isInstanceOf(HttpJettySolrClient.class);
        assertThat(SolrClientType.of("HttpJettySolrClient").create(url))
                .isInstanceOf(HttpJettySolrClient.class);

        solr = SolrClientType.CONCURRENT_UPDATE_HTTP2.create(url);
        assertThat(solr).isInstanceOf(ConcurrentUpdateJettySolrClient.class);

        solr = SolrClientType.LB_HTTP2.create(url);
        assertThat(solr).isInstanceOf(LBJettySolrClient.class);
        assertThat(SolrClientType.LB_HTTP2).hasToString("LBJettySolrClient");

        solr = SolrClientType.HTTP_JDK.create(url);
        assertThat(solr).isInstanceOf(HttpJdkSolrClient.class);

        assertThat(SolrClientType.of(null)).isNull();

        // In SolrJ 10, CloudSolrClient construction is lazy — no exception
        // is thrown at build time; connection errors only surface on use.
        solr = SolrClientType.CLOUD.create(url);
        assertThat(solr).isInstanceOf(CloudSolrClient.class);
        solr.close();
    }
}
