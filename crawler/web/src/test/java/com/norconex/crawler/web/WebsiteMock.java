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

import static org.apache.commons.lang3.StringUtils.appendIfMissing;
import static org.apache.commons.lang3.StringUtils.leftPad;
import static org.apache.commons.lang3.StringUtils.prependIfMissing;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.substringBeforeLast;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.HTML_UTF_8;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

public final class WebsiteMock {

    private WebsiteMock() {}

    public static void whenInfiniteDepth(ClientAndServer client) {
        client
            .when(request())
            .respond(WebsiteMock.responseWithInfiniteDepth());
    }

    public static void whenJsRenderedWebsite(ClientAndServer client) {
        whenResourceWebSite(client, "website/js-rendered")
            .whenHtml("/apple.html")
            .whenHtml("/carrot.html")
            .whenHtml("/celery.html")
            .whenHtml("/fruits.html")
            .whenHtml("/index.html")
            .whenHtml("/orange.html")
            .whenHtml("/vegetables.html")
            .whenJPG("/apple.jpg")
            .whenPDF("/tiny.pdf")
            ;
    }

    public static ResourceWebSite whenResourceWebSite(
            ClientAndServer client, String resourceBasePath) {
        return new ResourceWebSite(client, resourceBasePath);
    }

    public static class ResourceWebSite {
        private final String resourceBasePath;
        private final ClientAndServer client;
        private ResourceWebSite(
                ClientAndServer client, String resourceBasePath) {
            this.client = client;
            this.resourceBasePath = removeEnd(resourceBasePath, "/");
        }
        public ResourceWebSite whenHtml(String path) {
            var p = prependIfMissing(path, "/");
            WebsiteMock.whenHtml(client, p,
                    new TestResource(resourceBasePath + p).asString());
            return this;
        }
        public ResourceWebSite whenPDF(String path) {
            var p = prependIfMissing(path, "/");
            WebsiteMock.whenPDF(client, p,
                    new TestResource(resourceBasePath + p));
            return this;
        }
        public ResourceWebSite whenJPG(String path) {
            var p = prependIfMissing(path, "/");
            WebsiteMock.whenJPG(client, p,
                    new TestResource(resourceBasePath + p));
            return this;
        }
    }

    /**
     * Returns a page containing a link backward (if not the first page)
     * and a link forward, based on the appended number.
     * The number is zero-padded up to 4 characters (to facilitate sorting).
     * @return page
     */
    public static ExpectationResponseCallback responseWithInfiniteDepth() {
        return req -> {
            var reqPath = req.getPath().toString();
            var numStr = StringUtils.substringAfterLast(reqPath, "/");
            var basePath = appendIfMissing(
                    substringBeforeLast(reqPath, "/"), "/");
            var curDepth = 0;
            if (NumberUtils.isDigits(numStr)) {
                curDepth = Integer.parseInt(numStr);
            }

            var prevLink = "";
            if (curDepth > 0) {
                var beforeNum = leftPad(Integer.toString(curDepth - 1), 4, '0');
                prevLink = "<a href=\"%s\">Previous</a> | "
                        .formatted(basePath + beforeNum);
            }
            var afterNum = leftPad(Integer.toString(curDepth + 1), 4, '0');
            var nextLink = " | <a href=\"%s\">Next</a>"
                    .formatted(basePath + afterNum);
            return response().withBody(WebsiteMock.htmlPage().body(
                """
                <h1>%s test page</h1>
                %s Current page depth: %s %s
                """
                .formatted(reqPath, prevLink, curDepth, nextLink))
                .build(), HTML_UTF_8);
        };
    }

    public static String secureServerUrl(
            ClientAndServer client, String urlPath) {
        return serverUrl(client, urlPath).replace("http://", "https://");
    }
    public static String serverUrl(ClientAndServer client, String urlPath) {
        return "http://localhost:%s%s".formatted(
                client.getLocalPort(),
                StringUtils.prependIfMissing(urlPath, "/"));
    }
    public static String serverUrl(HttpRequest request, String urlPath) {
        return "http://localhost:%s%s".formatted(
                StringUtils.substringAfterLast(request.getLocalAddress(), ":"),
                StringUtils.prependIfMissing(urlPath, "/"));
    }

    public static Expectation[] whenHtml(
            ClientAndServer client, String urlPath, String body) {
        return client
            .when(request().withPath(urlPath))
            .respond(response().withBody(
                    WebsiteMock.htmlPage().body(body).build(), HTML_UTF_8));
    }

    public static Expectation[] whenHtml(
            ClientAndServer client, String urlPath, TestResource resource)
                    throws IOException {
        return client
            .when(request().withPath(urlPath))
            .respond(response().withBody(resource.asString(), HTML_UTF_8));
    }

    public static Expectation[] whenPNG(
            ClientAndServer client, String urlPath, TestResource resource)
                    throws IOException {
        return client
            .when(request().withPath(urlPath))
            .respond(response().withBody(
                    BinaryBody.binary(resource.asBytes(), MediaType.PNG))
        );
    }

    public static Expectation[] whenJPG(
            ClientAndServer client, String urlPath, TestResource resource) {
        return client
            .when(request().withPath(urlPath))
            .respond(response().withBody(
                    BinaryBody.binary(resource.asBytes(), MediaType.JPEG))
        );
    }

    public static Expectation[] whenPDF(
            ClientAndServer client, String urlPath, TestResource resource) {
        return client
            .when(request().withPath(urlPath))
            .respond(response().withBody(
                    BinaryBody.binary(resource.asBytes(), MediaType.PDF))
        );
    }


    @Data
    @Accessors(fluent = true)
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class HtmlPage {
        String title = "Mock HTML Page";
        //TODO if needed, replace with collections, of scripts, css, etc.
        String head = "";
        String body = "Mock HTML page content.";
        public String build() {
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <title>%s</title>
                  %s
                </head>
                <body>
                %s
                </body>
                </html>
                """.formatted(title, head, body);
        }
    }

    public static HtmlPage htmlPage() {
        return new HtmlPage();
    }
}
