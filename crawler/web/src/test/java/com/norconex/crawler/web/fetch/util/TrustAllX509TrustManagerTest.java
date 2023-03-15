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

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.net.Socket;

import javax.net.ssl.SSLEngine;

import org.junit.jupiter.api.Test;

class TrustAllX509TrustManagerTest {

    @Test
    void test() {
        assertThatNoException().isThrownBy(() -> {
            var tm = new TrustAllX509TrustManager();
            tm.getAcceptedIssuers();
            tm.checkClientTrusted(null, null);
            tm.checkServerTrusted(null, null);
            tm.checkClientTrusted(null, null, (Socket) null);
            tm.checkServerTrusted(null, null, (Socket) null);
            tm.checkClientTrusted(null, null, (SSLEngine) null);
            tm.checkServerTrusted(null, null, (SSLEngine) null);
        });
    }
}
