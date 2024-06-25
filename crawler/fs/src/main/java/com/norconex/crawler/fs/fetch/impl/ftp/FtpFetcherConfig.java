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
package com.norconex.crawler.fs.fetch.impl.ftp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.vfs2.provider.ftp.FtpFileType;
import org.apache.commons.vfs2.provider.ftps.FtpsDataChannelProtectionLevel;
import org.apache.commons.vfs2.provider.ftps.FtpsMode;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.crawler.fs.fetch.impl.BaseAuthVfsFetcherConfig;

import jakarta.xml.bind.annotation.XmlTransient;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * FTP (<code>ftp://</code>) and FTPS (<code>ftps://</code>) fetcher.
 * </p>
 *
 * {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher#doc}
 *
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.crawler.fs.fetch.impl.ftp.FtpFetcher">
 *
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 *
 *   {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher@nx.xml.usage}
 *
 *   <!-- FTP and FTPS: -->
 *
 *   <autodetectUtf8>[false|true]</autodetectUtf8>
 *   <connectTimeout>(milliseconds)</connectTimeout>
 *   <controlEncoding>...</controlEncoding>
 *   <controlKeepAliveTimeout>(milliseconds)</controlKeepAliveTimeout>
 *   <controlKeepAliveReplyTimeout>(milliseconds)</controlKeepAliveReplyTimeout>
 *   <dataTimeout>(milliseconds)</dataTimeout>
 *   <defaultDateFormat>...</defaultDateFormat>
 *   <fileType>[ASCII|BINARY|LOCAL|EBCDIC]</fileType>
 *   <passiveMode>[false|true]</passiveMode>
 *   <proxySettings>
 *     {@nx.include com.norconex.commons.lang.net.ProxySettings#usage}
 *   </proxySettings>
 *   <recentDateFormat>...</recentDateFormat>
 *   <remoteVerification>[false|true]</remoteVerification>
 *   <serverLanguageCode>...</serverLanguageCode>
 *   <serverTimeZoneId>...</serverTimeZoneId>
 *   <shortMonthNames>(comma-separated list)</shortMonthNames>
 *   <socketTimeout>(milliseconds)</socketTimeout>
 *   <transferAbortedOkReplyCodes>
 *     (comma-separated list of OK codes)
 *   </transferAbortedOkReplyCodes>
 *   <userDirIsRoot>[false|true]</userDirIsRoot>
 *   <mdtmLastModifiedTime>[false|true]</mdtmLastModifiedTime>
 *
 *   <!-- FTPS only: -->
 *   <connectionMode>[EXPLICIT|IMPLICIT]</connectionMode>
 *   <dataChannelProtectionLevel>[C|S|E|P]</dataChannelProtectionLevel>
 *
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <fetcher class="FtpFetcher">
 *   <ftpPassiveMode>true</ftpPassiveMode>
 *   <ftpUserDirIsRoot>false</ftpUserDirIsRoot>
 * </fetcher>
 * }
 * <p>
 * The above example sets the FTP settings required by some hosts to get file
 * listings on server directories.
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class FtpFetcherConfig extends BaseAuthVfsFetcherConfig {

    //TODO once we have all fetchers defined, have them all as default
    // fetchers in config

    //MAYBE: abstract FS enums and create equivalent?

    private boolean autodetectUtf8;
    private Duration connectTimeout;
    private String controlEncoding;
    private Duration dataTimeout;
    private String defaultDateFormat;
    private FtpFileType fileType;
    private boolean mdtmLastModifiedTime;
    private boolean passiveMode;
    @XmlTransient
    private final ProxySettings proxySettings = new ProxySettings();
    private String recentDateFormat;
    private boolean remoteVerificationDisabled;
    private String serverLanguageCode;
    private String serverTimeZoneId;
    @XmlTransient
    private final List<String> shortMonthNames = new ArrayList<>();
    private Duration socketTimeout;
    private Duration controlKeepAliveTimeout;
    private Duration controlKeepAliveReplyTimeout;
    private boolean userDirIsRoot;
    @XmlTransient
    private final List<Integer> transferAbortedOkReplyCodes = new ArrayList<>();

    // Only apply to FTPS
    private FtpsMode connectionMode;
    private FtpsDataChannelProtectionLevel dataChannelProtectionLevel;

    public List<Integer> getTransferAbortedOkReplyCodes() {
        return Collections.unmodifiableList(transferAbortedOkReplyCodes);
    }
    public final FtpFetcherConfig setTransferAbortedOkReplyCodes(
            List<Integer> transferAbortedOkReplyCodes) {
        CollectionUtil.setAll(
                this.transferAbortedOkReplyCodes, transferAbortedOkReplyCodes);
        return this;
    }
    public List<String> getShortMonthNames() {
        return Collections.unmodifiableList(shortMonthNames);
    }
    public final FtpFetcherConfig setShortMonthNames(
            List<String> shortMonthNames) {
        CollectionUtil.setAll(this.shortMonthNames, shortMonthNames);
        return this;
    }
}
