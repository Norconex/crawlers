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
package com.norconex.crawler.fs.fetch.impl.webdav;

import static com.norconex.commons.lang.encrypt.EncryptionUtil.decrypt;
import static com.norconex.commons.lang.encrypt.EncryptionUtil.decryptPassword;
import static com.norconex.crawler.fs.fetch.impl.FileFetchUtil.referenceStartsWith;

import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.provider.http5.Http5FileSystemConfigBuilder;

import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;


/**
 * <p>
 * Fetcher for WebDAV repositories. While it can also be used for general
 * HTTP requests, consider using Norconex Web Crawler for that.
 * </p>
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.crawler.fs.fetch.impl.WebDavFetcher">
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 *   {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher@nx.xml.usage}
 *   <connectionTimeout>(milliseconds)</connectionTimeout>
 *   <followRedirect>[false|true]</followRedirect>
 *   <hostnameVerificationEnabled>[false|true]</hostnameVerificationEnabled>
 *   <keepAlive>[false|true]</keepAlive>
 *   <keyStoreFile>...</keyStoreFile>
 *   <keyStorePass>...</keyStorePass>
 *   <keyStorePassKey>
 *     {@nx.include com.norconex.commons.lang.encrypt.EncryptionKey@nx.xml.usage}
 *   </keyStorePassKey>
 *   <keyStoreType>...</keyStoreType>
 *   <maxConnectionsPerHost>...</maxConnectionsPerHost>
 *   <maxTotalConnections>...</maxTotalConnections>
 *   <preemptiveAuth>[false|true]</preemptiveAuth>
 *   <proxySettings>
 *     {@nx.include com.norconex.commons.lang.net.ProxySettings#usage}
 *     <proxyDomain>...</proxyDomain>
 *   </proxySettings>
 *   <soTimeout>(milliseconds)</soTimeout>
 *   <tlsVersions>...</tlsVersions>
 *   <urlCharset>...</urlCharset>
 *   <userAgent>...</userAgent>
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <fetcher class="WebDavFetcher">
 *   <connectionTimeout>2 minutes</connectionTimeout>
 * </fetcher>
 * }
 */
@SuppressWarnings("javadoc")
@ToString
@EqualsAndHashCode
public class WebDavFetcher extends AbstractAuthVfsFetcher<WebDavFetcherConfig> {

    @Getter
    private final WebDavFetcherConfig configuration = new WebDavFetcherConfig();

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return referenceStartsWith(
                fetchRequest, "webdav://", "http://", "https://");
    }

    @ToString.Include(name = "keyStorePass")
    private String keyStorePassToString() {
        return "********";
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        var fs = Http5FileSystemConfigBuilder.getInstance();
        var cfg = configuration;

        fs.setConnectionTimeout(opts, cfg.getConnectionTimeout());
        fs.setFollowRedirect(opts, cfg.isFollowRedirect());
        fs.setHostnameVerificationEnabled(
                opts, cfg.isHostnameVerificationEnabled());
        fs.setKeepAlive(opts, cfg.isKeepAlive());
        fs.setKeyStoreFile(opts, cfg.getKeyStoreFile());
        fs.setKeyStorePass(
                opts, decrypt(cfg.getKeyStorePass(), cfg.getKeyStorePassKey()));
        fs.setKeyStoreType(opts, cfg.getKeyStoreType());
        fs.setMaxConnectionsPerHost(opts, cfg.getMaxConnectionsPerHost());
        fs.setMaxTotalConnections(opts, cfg.getMaxTotalConnections());
        fs.setPreemptiveAuth(opts, cfg.isPreemptiveAuth());
        if (cfg.getProxySettings().isSet()) {
            fs.setProxyAuthenticator(opts, new StaticUserAuthenticator(
                    cfg.getProxySettings().getCredentials().getUsername(),
                    decryptPassword(cfg.getProxySettings().getCredentials()),
                    cfg.getProxyDomain()));
            fs.setProxyHost(opts, cfg.getProxySettings().getHost().getName());
            fs.setProxyPort(opts, cfg.getProxySettings().getHost().getPort());
            fs.setProxyScheme(opts, cfg.getProxySettings().getScheme());
        }
        fs.setSoTimeout(opts, cfg.getSoTimeout());
        fs.setTlsVersions(opts, cfg.getTlsVersions());
        fs.setUrlCharset(opts, cfg.getUrlCharset());
        fs.setUserAgent(opts, cfg.getUserAgent());
    }
}
