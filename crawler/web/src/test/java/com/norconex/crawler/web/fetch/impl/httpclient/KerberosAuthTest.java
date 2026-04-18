/* Copyright 2026 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.norconex.crawler.web.fetch.impl.httpclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.net.Host;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.crawler.core.CrawlerException;

class KerberosAuthTest {

    @Test
    void testKerberosConfigWriteRead() {
        var authCfg = new HttpAuthConfig();
        authCfg.setMethod(HttpAuthMethod.SPNEGO);
        authCfg.setCredentials(
                new Credentials("user@REALM", "secret"));
        authCfg.setHost(new Host("server.example.com", 80));

        var krbCfg = new KerberosConfig()
                .setServicePrincipalName("HTTP/server.example.com")
                .setKrb5ConfigPath(Path.of("/etc/krb5.conf"))
                .setKeytabPath(Path.of("/etc/user.keytab"))
                .setPrincipal("user@REALM")
                .setUseTicketCache(false);
        authCfg.setKerberosConfig(krbCfg);

        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(
                        authCfg));
    }

    @Test
    void testKerberosConfigValues() {
        var krbCfg = new KerberosConfig()
                .setServicePrincipalName("HTTP/host.example.com")
                .setKrb5ConfigPath(Path.of("/etc/krb5.conf"))
                .setKeytabPath(Path.of("/etc/user.keytab"))
                .setPrincipal("user@EXAMPLE.COM")
                .setUseTicketCache(true)
                .setLoginModuleName("myModule");

        assertThat(krbCfg.getServicePrincipalName())
                .isEqualTo("HTTP/host.example.com");
        assertThat(krbCfg.getKrb5ConfigPath())
                .isEqualTo(Path.of("/etc/krb5.conf"));
        assertThat(krbCfg.getKeytabPath())
                .isEqualTo(Path.of("/etc/user.keytab"));
        assertThat(krbCfg.getPrincipal())
                .isEqualTo("user@EXAMPLE.COM");
        assertThat(krbCfg.isUseTicketCache()).isTrue();
        assertThat(krbCfg.getLoginModuleName())
                .isEqualTo("myModule");
    }

    @Test
    void testSpnegoWithoutKerberosConfigThrows() {
        // When SPNEGO is selected but no KerberosConfig is
        // set, creating a fetcher and credentials should fail.
        var fetcherCfg = new HttpClientFetcherConfig();
        var authCfg = new HttpAuthConfig();
        authCfg.setMethod(HttpAuthMethod.SPNEGO);
        authCfg.setCredentials(
                new Credentials("user@REALM", "secret"));
        authCfg.setHost(new Host("server.example.com", 80));
        // Intentionally NOT setting kerberosConfig
        fetcherCfg.setAuthentication(authCfg);

        var fetcher = new HttpClientFetcher();
        fetcher.getConfiguration().setAuthentication(authCfg);

        assertThatExceptionOfType(CrawlerException.class)
                .isThrownBy(fetcher::createCredentialsProvider)
                .withMessageContaining(
                        "Kerberos configuration is required");
    }

    @Test
    void testKerberosMethodWithoutKerberosConfigThrows() {
        var authCfg = new HttpAuthConfig();
        authCfg.setMethod(HttpAuthMethod.KERBEROS);
        authCfg.setCredentials(
                new Credentials("user@REALM", "secret"));
        authCfg.setHost(new Host("server.example.com", 80));

        var fetcher = new HttpClientFetcher();
        fetcher.getConfiguration().setAuthentication(authCfg);

        assertThatExceptionOfType(CrawlerException.class)
                .isThrownBy(fetcher::createCredentialsProvider)
                .withMessageContaining(
                        "Kerberos configuration is required");
    }
}
