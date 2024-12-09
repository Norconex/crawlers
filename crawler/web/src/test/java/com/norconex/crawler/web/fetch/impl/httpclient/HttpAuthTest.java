/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl.httpclient;

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.HTML_UTF_8;
import static org.mockserver.model.Not.not;
import static org.mockserver.model.Parameter.param;
import static org.mockserver.model.ParameterBody.params;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import com.norconex.commons.lang.security.Credentials;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpAuthConfig;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpAuthMethod;

@MockServerSettings
class HttpAuthTest {

    private final String loginFormPath = "/loginForm.html";
    private final String loginFormActionPath = "/loginFormAction";
    private final String wrongFormActionPath = "/wrongFormAction";
    private final String protectedPath = "/protected.html";
    @TempDir
    private Path tempDir;

    @Test
    void testBasicAuthentication(ClientAndServer client) {
        client.reset();
        var protectedUrl = serverUrl(client, protectedPath);

        client
                .when(
                        request(protectedPath)
                                .withHeader(
                                        "Authorization",
                                        "Basic Z29vZHVzZXI6Z29vZHBhc3N3b3Jk"))
                .respond(
                        response()
                                .withBody("You got it!"));
        client
                .when(request(protectedPath))
                .respond(
                        response()
                                .withStatusCode(
                                        HttpStatusCode.UNAUTHORIZED_401.code())
                                .withHeader(
                                        "WWW-Authenticate",
                                        "realm=\"Test Realm\", charset=\"UTF-8\""));

        // Good creds
        var mem = WebTestUtil.runWithConfig(tempDir, cfg -> {
            var fetchCfg = WebTestUtil.firstHttpFetcherConfig(cfg);
            var authCfg = new HttpAuthConfig();
            authCfg.setMethod(HttpAuthMethod.BASIC);
            authCfg.setPreemptive(true);
            authCfg.setCredentials(
                    new Credentials("gooduser", "goodpassword"));
            fetchCfg.setAuthentication(authCfg);
            // Misc. unaffecting params that should not break
            fetchCfg.setSslProtocols(List.of("TLS 1.3"));
            fetchCfg.setETagDisabled(true);
            fetchCfg.setHstsDisabled(true);
            fetchCfg.setIfModifiedSinceDisabled(true);
            fetchCfg.setSniDisabled(true);
            cfg.setStartReferences(List.of(protectedUrl));
        });
        assertThat(mem.getUpsertCount()).isOne();
        var doc = mem.getUpsertRequests().get(0);
        assertThat(WebTestUtil.docText(doc)).isEqualTo("You got it!");

        // Bad creds
        mem = WebTestUtil.runWithConfig(tempDir, cfg -> {
            var authCfg = new HttpAuthConfig();
            authCfg.setMethod(HttpAuthMethod.BASIC);
            authCfg.setPreemptive(true);
            authCfg.setCredentials(
                    new Credentials("baduser", "badpassword"));
            var fetchCfg = WebTestUtil.firstHttpFetcherConfig(cfg);
            fetchCfg.setAuthentication(authCfg);
            cfg.setStartReferences(List.of(protectedUrl));
        });

        assertThat(mem.getUpsertCount()).isZero();
    }

    @Test
    void testFormAuthentication(ClientAndServer client) {
        client.reset();
        var loginFormUrl = serverUrl(client, loginFormPath);
        var loginFormActionUrl = serverUrl(client, loginFormActionPath);
        var protectedUrl = serverUrl(client, protectedPath);

        whenLoginRequired(client);

        // Fill and submit form with good credentials
        var mem = WebTestUtil.runWithConfig(tempDir.resolve("1"), cfg -> {
            cfg.setStartReferences(List.of(protectedUrl));
            var fetchCfg = WebTestUtil.firstHttpFetcherConfig(cfg);
            fetchCfg.setAuthentication(
                    authConfirm(
                            loginFormUrl, "gooduser", "goodpassword"));
        });
        var doc = mem.getUpsertRequests().get(0);
        assertThat(WebTestUtil.docText(doc)).isEqualTo("You got it!");

        // Fill and submit form with bad credentials
        mem = WebTestUtil.runWithConfig(tempDir.resolve("2"), cfg -> {
            cfg.setStartReferences(List.of(protectedUrl));
            var fetchCfg = WebTestUtil.firstHttpFetcherConfig(cfg);
            fetchCfg.setAuthentication(
                    authConfirm(
                            loginFormUrl, "baduser", "badpassword"));
        });
        assertThat(mem.getUpsertCount()).isZero();

        // Invoke form action URL directly with good credentials
        mem = WebTestUtil.runWithConfig(tempDir.resolve("3"), cfg -> {
            cfg.setStartReferences(List.of(protectedUrl));
            var fetchCfg = WebTestUtil.firstHttpFetcherConfig(cfg);
            var authCfg = authConfirm(
                    loginFormActionUrl, "gooduser", "goodpassword");
            authCfg.setFormSelector(null);
            fetchCfg.setAuthentication(authCfg);
        });
        assertThat(mem.getUpsertRequests()).isNotEmpty();
        doc = mem.getUpsertRequests().get(0);
        assertThat(WebTestUtil.docText(doc)).isEqualTo("You got it!");

        // Invoke form action URL directly with bad credentials
        mem = WebTestUtil.runWithConfig(tempDir.resolve("4"), cfg -> {
            cfg.setStartReferences(List.of(protectedUrl));
            var fetchCfg = WebTestUtil.firstHttpFetcherConfig(cfg);
            var authCfg = authConfirm(
                    loginFormActionUrl, "baduser", "badpassword");
            authCfg.setFormSelector(null);
            fetchCfg.setAuthentication(authCfg);
        });
        assertThat(mem.getUpsertCount()).isZero();
    }

    private HttpAuthConfig authConfirm(
            String formUrl, String username, String password) {
        var authCfg = new HttpAuthConfig();
        authCfg.setCredentials(new Credentials(username, password));
        authCfg.setFormSelector("#thisOne");
        authCfg.setFormUsernameField("THEusername");
        authCfg.setFormPasswordField("THEpassword");
        authCfg.setMethod(HttpAuthMethod.FORM);
        authCfg.setUrl(formUrl);
        return authCfg;
    }

    private void whenLoginRequired(ClientAndServer client) {
        client
                .when(request(loginFormPath))
                .respond(
                        response()
                                .withBody(
                                        WebsiteMock.htmlPage()
                                                .body(
                                                        """
                                                                <form id="notThisOne" action="%s">Not this one</form>
                                                                <form id="thisOne" action="%s" method="POST">
                                                                  Username: <input type="text=" name="THEusername"><br>
                                                                  Password: <input type="password=" name="THEpassword"><br>
                                                                  <input type="submit" value="Login"><br>
                                                                </form>
                                                                """
                                                                .formatted(
                                                                        wrongFormActionPath,
                                                                        loginFormActionPath))
                                                .build(),
                                        HTML_UTF_8));
        client
                .when(
                        request(wrongFormActionPath)
                                .withMethod("POST"))
                .respond(HttpResponse.notFoundResponse());
        client
                .when(
                        request(loginFormActionPath)
                                .withMethod("POST")
                                .withBody(
                                        params(
                                                param(
                                                        "THEusername",
                                                        "gooduser"),
                                                param(
                                                        "THEpassword",
                                                        "goodpassword"))))
                .respond(
                        response()
                                .withStatusCode(
                                        HttpStatusCode.ACCEPTED_202.code())
                                .withBody("LOGIN SUCCESS")
                                .withCookie("userToken", "joe"));

        client
                .when(
                        request(loginFormActionPath)
                                .withMethod("POST")
                                .withBody(
                                        not(
                                                params(
                                                        param(
                                                                "THEusername",
                                                                "gooduser"),
                                                        param(
                                                                "THEpassword",
                                                                "goodpassword")))))
                .respond(
                        response()
                                .withStatusCode(
                                        HttpStatusCode.FORBIDDEN_403.code())
                                .withBody("LOGIN FAILED"));

        client
                .when(
                        request(protectedPath)
                                .withCookie("userToken", "joe"))
                .respond(
                        response()
                                .withBody("You got it!"));
        client
                .when(request(protectedPath))
                .respond(
                        response()
                                .withStatusCode(
                                        HttpStatusCode.FORBIDDEN_403.code())
                                .withBody("DENIED"));
    }
}
