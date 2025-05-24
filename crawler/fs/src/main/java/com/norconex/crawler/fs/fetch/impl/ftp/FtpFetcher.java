/* Copyright 2023-2025 Norconex Inc.
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

import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher;
import com.norconex.crawler.fs.fetch.impl.sftp.SftpFetcher;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * <p>
 * FTP (<code>ftp://</code>) and FTPS (<code>ftps://</code>) fetcher.
 * </p>
 *
 * @see SftpFetcher
 */
@ToString
@EqualsAndHashCode
public class FtpFetcher extends AbstractAuthVfsFetcher<FtpFetcherConfig> {

    @Getter
    private final FtpFetcherConfig configuration = new FtpFetcherConfig();

    @Override
    protected boolean acceptFileRequest(@NonNull FileFetchRequest fetchRequest) {
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
