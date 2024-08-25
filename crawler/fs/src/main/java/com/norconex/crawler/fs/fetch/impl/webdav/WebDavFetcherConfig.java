/* Copyright 2023-2024 Norconex Inc.
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

import java.time.Duration;

import com.norconex.commons.lang.encrypt.EncryptionKey;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.crawler.fs.fetch.impl.BaseAuthVfsFetcherConfig;

import jakarta.xml.bind.annotation.XmlTransient;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

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
@Data
@Accessors(chain = true)
public class WebDavFetcherConfig extends BaseAuthVfsFetcherConfig {

    //MAYBE ACL?

    private Duration connectionTimeout;
    private boolean followRedirect;
    private boolean hostnameVerificationEnabled;
    private boolean keepAlive;
    private String keyStoreFile;
    @ToString.Exclude
    private String keyStorePass;
    @XmlTransient
    private EncryptionKey keyStorePassKey;
    private String keyStoreType;
    private int maxConnectionsPerHost = 5;
    private int maxTotalConnections = 50;
    private boolean preemptiveAuth;
    @XmlTransient
    private final ProxySettings proxySettings = new ProxySettings();
    @XmlTransient
    private String proxyDomain;
    private Duration soTimeout;
    private String tlsVersions;
    private String urlCharset;
    private String userAgent;

    @ToString.Include(name = "keyStorePass")
    private String keyStorePassToString() {
        return "********";
    }
}
