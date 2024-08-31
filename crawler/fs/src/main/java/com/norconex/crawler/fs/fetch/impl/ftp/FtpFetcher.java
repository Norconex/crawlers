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

import static com.norconex.crawler.fs.fetch.impl.FileFetchUtil.referenceStartsWith;
import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

import java.util.Optional;

import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder;

import com.norconex.commons.lang.time.DurationParser;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

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
@ToString
@EqualsAndHashCode
public class FtpFetcher extends AbstractAuthVfsFetcher<FtpFetcherConfig> {

    @Getter
    private final FtpFetcherConfig configuration = new FtpFetcherConfig();

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return referenceStartsWith(fetchRequest, "ftp://", "ftps://");
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        var cfg = configuration;
        var ftp = FtpsFileSystemConfigBuilder.getInstance();
        ftp.setFtpsMode(opts, cfg.getConnectionMode());
        ftp.setDataChannelProtectionLevel(
                opts, cfg.getDataChannelProtectionLevel());
        ftp.setAutodetectUtf8(opts, cfg.isAutodetectUtf8());
        ftp.setConnectTimeout(opts, cfg.getConnectTimeout());
        ftp.setControlEncoding(opts, cfg.getControlEncoding());
        ftp.setDataTimeout(opts, cfg.getDataTimeout());
        ftp.setDefaultDateFormat(opts, cfg.getDefaultDateFormat());
        ftp.setFileType(opts, cfg.getFileType());
        ftp.setPassiveMode(opts, cfg.isPassiveMode());
        Optional.ofNullable(cfg.getProxySettings().toProxy()).ifPresent(
                p -> ftp.setProxy(opts, p));
        ftp.setRecentDateFormat(opts, cfg.getRecentDateFormat());
        ftp.setRemoteVerification(opts, !cfg.isRemoteVerificationDisabled());
        ftp.setServerLanguageCode(opts, cfg.getServerLanguageCode());
        ftp.setServerTimeZoneId(opts, cfg.getServerTimeZoneId());
        ftp.setShortMonthNames(
                opts,
                cfg.getShortMonthNames().toArray(EMPTY_STRING_ARRAY));
        ftp.setSoTimeout(opts, cfg.getSocketTimeout());
        ftp.setControlKeepAliveTimeout(opts, cfg.getControlKeepAliveTimeout());
        ftp.setControlKeepAliveReplyTimeout(
                opts, cfg.getControlKeepAliveReplyTimeout());
        ftp.setUserDirIsRoot(opts, cfg.isUserDirIsRoot());
        ftp.setTransferAbortedOkReplyCodes(
                opts, cfg.getTransferAbortedOkReplyCodes());
        ftp.setMdtmLastModifiedTime(opts, cfg.isMdtmLastModifiedTime());

        //NOTE: Override this method if there is a need to set additional
        //options, such as entryParserFactory, keyManager, or trustManager.
    }
}
