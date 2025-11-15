/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.cluster.admin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Function;

import com.norconex.crawler.core.CrawlConfig;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class ClusterAdminClient {
    public static final String DEFAULT_NODE_URL =
            "http://localhost:" + CrawlConfig.DEFAULT_CLUSTER_PORT;

    @Singular
    private List<String> nodeUrls;
    @NonNull
    private final String crawlerId;

    public boolean clusterStop() {
        var resp = tryNodes(url -> send(httpPost(url, Endpoint.CLUSTER_STOP)));
        if (resp.statusCode() == 200) {
            LOG.info("Stop request sent to cluster.");
            return true;
        }
        LOG.error("Could not send stop request. Message: {}", resp.body());
        return false;
    }

    public int clusterSize() {
        var resp = tryNodes(url -> send(httpGet(url, Endpoint.CLUSTER_SIZE)));
        return Integer.parseInt(resp.body());
    }

    private HttpRequest httpGet(String nodeUrl, Endpoint endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(nodeUrl + endpoint))
                .header("crawler-id", crawlerId)
                .GET()
                .build();
    }

    private HttpRequest httpPost(String nodeUrl, Endpoint endpoint) {
        return HttpRequest.newBuilder()
                .uri(URI.create(nodeUrl + endpoint))
                .header("crawler-id", crawlerId)
                .POST(BodyPublishers.noBody())
                .build();
    }

    private HttpResponse<String>
            tryNodes(Function<String, HttpResponse<String>> f) {
        var urls = !nodeUrls.isEmpty()
                ? nodeUrls
                : List.of(DEFAULT_NODE_URL);
        HttpResponse<String> lastResponse = null;
        for (String url : urls) {
            lastResponse = f.apply(url);
            if (lastResponse.statusCode() == 200) {
                return lastResponse;
            }
        }
        return lastResponse;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClusterAdminException(e);
        } catch (IOException e) {
            throw new ClusterAdminException(e);
        }
    }
}
