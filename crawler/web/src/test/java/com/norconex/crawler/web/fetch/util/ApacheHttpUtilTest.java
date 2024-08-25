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
package com.norconex.crawler.web.fetch.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URISyntaxException;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.crawler.web.fetch.impl.HttpAuthConfig;

class ApacheHttpUtilTest {

    @Test
    void testFormToRequest() throws URISyntaxException {
        var html = """
                <form
                  action="/login"
                  method="%s"
                  accept-charset="UTF-8"
                  %s
                >
                  Username: <input type="text=" name="THEusername"><br>
                  Password: <input type="password=" name="THEpassword"><br>
                  <input type="submit" value="Login"><br>
                </form>
                """;

        var authCfg = new HttpAuthConfig();
        authCfg.setFormSelector("form");
        authCfg.setCredentials(new Credentials("joe", "dalton"));
        authCfg.setFormUsernameField("THEusername");
        authCfg.setFormPasswordField("THEpassword");

        // POST, default enctype
        var doc = Jsoup.parse(
                WebsiteMock
                        .htmlPage()
                        .body(html.formatted("POST", ""))
                        .build(),
                "http://blah.com"
        );
        var req = (HttpPost) ApacheHttpUtil.formToRequest(doc, authCfg);
        assertThat(req).isNotNull();
        assertThat(req.getEntity()).isInstanceOf(UrlEncodedFormEntity.class);

        // POST, multipart/form-data
        doc = Jsoup.parse(
                WebsiteMock
                        .htmlPage()
                        .body(
                                html.formatted(
                                        "POST",
                                        "encType=\"multipart/form-data\""
                                )
                        )
                        .build(),
                "http://blah.com"
        );
        req = (HttpPost) ApacheHttpUtil.formToRequest(doc, authCfg);
        assertThat(req).isNotNull();
        assertThat(req.getEntity().getClass().getSimpleName())
                .isEqualTo("MultipartFormEntity");

        // POST, text/plain
        doc = Jsoup.parse(
                WebsiteMock
                        .htmlPage()
                        .body(html.formatted("POST", "encType=\"text/plain\""))
                        .build(),
                "http://blah.com"
        );
        req = (HttpPost) ApacheHttpUtil.formToRequest(doc, authCfg);
        assertThat(req).isNotNull();
        assertThat(req.getEntity()).isInstanceOf(StringEntity.class);

        // GET
        doc = Jsoup.parse(
                WebsiteMock
                        .htmlPage()
                        .body(html.formatted("GET", ""))
                        .build(),
                "http://blah.com"
        );
        var get = (HttpGet) ApacheHttpUtil.formToRequest(doc, authCfg);
        assertThat(get).isNotNull();

        assertThat(HttpURL.toURI(get.getRequestUri()))
                .hasParameter("THEusername")
                .hasParameter("THEpassword");

        // HEAD
        doc = Jsoup.parse(
                WebsiteMock
                        .htmlPage()
                        .body(html.formatted("HEAD", ""))
                        .build(),
                "http://blah.com"
        );
        var head = (HttpGet) ApacheHttpUtil.formToRequest(doc, authCfg);
        assertThat(head).isNull();

        // No form
        assertThat(
                ApacheHttpUtil.formToRequest(
                        Jsoup.parse("<html>No form!</html>"), authCfg
                )
        ).isNull();
    }
}
