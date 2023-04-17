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

import java.time.Duration;

import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.provider.http5.Http5FileSystemConfigBuilder;

import com.norconex.commons.lang.encrypt.EncryptionKey;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import lombok.Data;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;


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
@FieldNameConstants
@XmlRootElement(name = "fetcher")
@XmlAccessorType(XmlAccessType.FIELD)
public class WebDavFetcher extends AbstractAuthVfsFetcher {

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
        var cfg = Http5FileSystemConfigBuilder.getInstance();

        cfg.setConnectionTimeout(opts, connectionTimeout);
        cfg.setFollowRedirect(opts, followRedirect);
        cfg.setHostnameVerificationEnabled(opts, hostnameVerificationEnabled);
        cfg.setKeepAlive(opts, keepAlive);
        cfg.setKeyStoreFile(opts, keyStoreFile);
        cfg.setKeyStorePass(opts, decrypt(keyStorePass, keyStorePassKey));
        cfg.setKeyStoreType(opts, keyStoreType);
        cfg.setMaxConnectionsPerHost(opts, maxConnectionsPerHost);
        cfg.setMaxTotalConnections(opts, maxTotalConnections);
        cfg.setPreemptiveAuth(opts, preemptiveAuth);
        if (proxySettings.isSet()) {
            cfg.setProxyAuthenticator(opts, new StaticUserAuthenticator(
                    proxySettings.getCredentials().getUsername(),
                    decryptPassword(proxySettings.getCredentials()),
                    proxyDomain));
            cfg.setProxyHost(opts, proxySettings.getHost().getName());
            cfg.setProxyPort(opts, proxySettings.getHost().getPort());
            cfg.setProxyScheme(opts, proxySettings.getScheme());
        }
        cfg.setSoTimeout(opts, soTimeout);
        cfg.setTlsVersions(opts, tlsVersions);
        cfg.setUrlCharset(opts, urlCharset);
        cfg.setUserAgent(opts, userAgent);
    }

    @Override
    protected void loadFetcherFromXML(XML xml) {
        super.loadFetcherFromXML(xml);
        xml.ifXML(Fields.proxySettings, proxyXML -> {
            proxySettings.loadFromXML(proxyXML);
            setProxyDomain(proxyXML.getString(Fields.proxyDomain, proxyDomain));
        });
        setKeyStorePassKey(EncryptionKey.loadFromXML(
                xml.getXML(Fields.keyStorePassKey), keyStorePassKey));
    }
    @Override
    protected void saveFetcherToXML(XML xml) {
        super.saveFetcherToXML(xml);
        var proxyXML = xml.addElement(Fields.proxySettings);
        proxySettings.saveToXML(proxyXML);
        proxyXML.addElement(Fields.proxyDomain, proxyDomain);
        EncryptionKey.saveToXML(
                xml.addElement(Fields.keyStorePassKey), keyStorePassKey);
    }
}
