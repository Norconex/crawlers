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
package com.norconex.committer.solr;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateHttp2SolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.LBHttp2SolrClient;
import org.apache.solr.client.solrj.impl.LBHttpSolrClient;

/**
 * Supported
 * <a href="https://lucene.apache.org/solr/guide/8_1/using-solrj.html#types-of-solrclients">
 * SolrClient</a> types.
 * @author Pascal Essiembre
 * @since 2.4.0
 */
public enum SolrClientType {

    HTTP("HttpSolrClient", url -> new HttpSolrClient.Builder(url).build()),

    LB_HTTP("LBHttpSolrClient",
            url -> new LBHttpSolrClient.Builder().withBaseSolrUrls(
                    url.split("[,\\s]+")).build()),

    CONCURRENT_UPDATE("ConcurrentUpdateSolrClient",
            url -> new ConcurrentUpdateSolrClient.Builder(url).build()),

    CLOUD("CloudSolrClient", url -> {
        List<String> urls = Arrays.asList(url.split("[,\\s]+"));
        if (url.startsWith("http")) {
            return new CloudSolrClient.Builder(urls).build();
        }
        return new CloudSolrClient.Builder(urls, Optional.empty()).build();
    }),
    HTTP2("Http2SolrClient", (url) -> new Http2SolrClient.Builder(url).build()),

    LB_HTTP2("LBHttp2SolrClient", url -> new LBHttp2SolrClient(
            new Http2SolrClient.Builder().build(), url.split("[,\\s]+"))),

    CONCURRENT_UPDATE_HTTP2("ConcurrentUpdateHttp2SolrClient", url ->
            new ConcurrentUpdateHttp2SolrClient.Builder(
                    url, new Http2SolrClient.Builder().build()).build())
    ;

    private final String type;
    private final Function<String, SolrClient> clientFactory;
    SolrClientType(String type, Function<String, SolrClient> f) {
        this.type = type;
        this.clientFactory = f;
    }

    @Override
    public String toString() {
        return type;
    }

    public SolrClient create(String solrURL) {
        Objects.requireNonNull(solrURL, "'solrURL' must not be null.");
        return clientFactory.apply(solrURL);
    }

    public static SolrClientType of(String type) {
        if (type == null) {
            return null;
        }
        for (SolrClientType t : SolrClientType.values()) {
            if (t.toString().equalsIgnoreCase(type)) {
                return t;
            }
        }
        return null;
    }
}