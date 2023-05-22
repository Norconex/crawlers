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
package com.norconex.committer.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;

import org.apache.solr.client.solrj.impl.ConcurrentUpdateHttp2SolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.LBHttp2SolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class SolrClientTypeTest {

    @Test
    void testSolrClientType() throws IOException {
        var url = "http://solr/base-url";

        var solr = SolrClientType.HTTP.create(url);
        assertThat(solr).isInstanceOf(HttpSolrClient.class);
        assertThat(((HttpSolrClient) solr).getBaseURL()).isEqualTo(url);

        solr = SolrClientType.CONCURRENT_UPDATE.create(url);
        assertThat(solr).isInstanceOf(ConcurrentUpdateSolrClient.class);

        solr = SolrClientType.LB_HTTP.create(url);
        assertThat(solr).isInstanceOf(LBHttpSolrClient.class);

        solr = SolrClientType.HTTP2.create(url);
        assertThat(solr).isInstanceOf(Http2SolrClient.class);
        assertThat(SolrClientType.of("Http2SolrClient").create(url))
            .isInstanceOf(Http2SolrClient.class);

        solr = SolrClientType.CONCURRENT_UPDATE_HTTP2.create(url);
        assertThat(solr).isInstanceOf(ConcurrentUpdateHttp2SolrClient.class);

        solr = SolrClientType.LB_HTTP2.create(url);
        assertThat(solr).isInstanceOf(LBHttp2SolrClient.class);
        assertThat(SolrClientType.LB_HTTP2).hasToString("LBHttp2SolrClient");

        assertThat(SolrClientType.of(null)).isNull();

        // needs a real cluster to be initialized, we don't have on in this
        // test, so should fail.
        assertThatExceptionOfType(RuntimeException.class).isThrownBy(
                () -> SolrClientType.CLOUD.create(url));
    }
}
