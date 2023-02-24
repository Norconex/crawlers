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
package com.norconex.crawler.web;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.IOException;

import org.mockserver.integration.ClientAndServer;
import org.mockserver.mock.Expectation;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.MediaType;

public final class WebsiteMock {

    private WebsiteMock() {}

    public static Expectation[] whenHtml(
            ClientAndServer client, String urlPath, TestResource resource)
                    throws IOException {
        return client.when(
            request()
                .withPath(urlPath)
        )
        .respond(
            response()
              .withBody(resource.asString(), MediaType.HTML_UTF_8)
        );
    }

    public static Expectation[] whenImagePng(
            ClientAndServer client, String urlPath, TestResource resource)
                    throws IOException {
        return client.when(
            request()
                .withPath(urlPath)
        )
        .respond(
            response()
            .withBody(BinaryBody.binary(resource.asBytes(), MediaType.PNG))
        );
    }
}
