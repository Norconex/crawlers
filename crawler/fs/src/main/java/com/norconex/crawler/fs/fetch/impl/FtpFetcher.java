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
package com.norconex.crawler.fs.fetch.impl;

import static com.norconex.crawler.fs.fetch.impl.FileFetchUtil.referenceStartsWith;

import java.net.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.KeyManager;
import javax.net.ssl.TrustManager;

import org.apache.commons.net.ftp.parser.FTPFileEntryParserFactory;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.ftp.FtpFileType;
import org.apache.commons.vfs2.provider.ftps.FtpsDataChannelProtectionLevel;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.ftps.FtpsMode;

import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.fs.fetch.FileFetchRequest;

import lombok.Data;
import lombok.NonNull;

/**
 * FTP (<code>ftp://</code>) and FTPS (<code>ftps://</code>) fetcher.
 */
@Data
public class FtpFetcher extends AbstractVfsFetcher {


    //TODO document that some settings have to be set programatically?


    //TODO instead, have GenericFsFetcher include all CommonVFS.
    // makes the most sense since we can't have multiple settings for
    // each.

    // See if we can have singleton per crawler at least (via some classloader
    // trick).






    //TODO abstract FS enums and create our equivalents?
//    public enum SecureMode {EXPLICIT, IMPLICIT}
//    public enum FileType {ASCII, BINARY, LOCAL, EBCDIC}

    //TODO document that changes of those properties once started have
    // no effect.
    private boolean autodetectUtf8;
    private Duration connectTimeout;
    private String controlEncoding;
    private Duration dataTimeout;
    private String defaultDateFormat;
    private FTPFileEntryParserFactory entryParserFactory;
    private FtpFileType fileType;
    private boolean passiveMode;
    private Proxy proxy;
    private String recentDateFormat;
    private boolean remoteVerification;
    private String serverLanguageCode;
    private String serverTimeZoneId;
    private String[] shortMonthNames;
    private Duration socketTimeout;
    private Duration controlKeepAliveTimeout;
    private Duration controlKeepAliveReplyTimeout;
    private boolean userDirIsRoot;
    private final List<Integer> transferAbortedOkReplyCodes = new ArrayList<>();
    private boolean mdtmLastModifiedTime;

    // only when secure is true
    private boolean secure;
    private FtpsMode connectionMode;
    private FtpsDataChannelProtectionLevel dataChannelProtectionLevel;
    private KeyManager keyManager;
    private TrustManager trustManager;

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return referenceStartsWith(fetchRequest, "ftp://", "ftps://");
    }

    @Override
    protected FileSystemOptions createFileSystemOptions() {
        var opts = new FileSystemOptions();
        FtpFileSystemConfigBuilder ftp;
        if (secure) {
            var ftps = FtpsFileSystemConfigBuilder.getInstance();
            ftps.setFtpsMode(opts, connectionMode);
            ftps.setDataChannelProtectionLevel(
                    opts, dataChannelProtectionLevel);
            ftps.setKeyManager(opts, keyManager);
            ftps.setTrustManager(opts, trustManager);
            ftp = ftps;
        } else {
            ftp = FtpFileSystemConfigBuilder.getInstance();
        }
        ftp.setAutodetectUtf8(opts, autodetectUtf8);
        ftp.setConnectTimeout(opts, connectTimeout);
        ftp.setControlEncoding(opts, controlEncoding);
        ftp.setDataTimeout(opts, dataTimeout);
        ftp.setDefaultDateFormat(opts, defaultDateFormat);
        ftp.setEntryParserFactory(opts, entryParserFactory);
        ftp.setFileType(opts, fileType);
        ftp.setPassiveMode(opts, passiveMode);
        ftp.setProxy(opts, proxy);
        ftp.setRecentDateFormat(opts, recentDateFormat);
        ftp.setRemoteVerification(opts, remoteVerification);
        ftp.setServerLanguageCode(opts, serverLanguageCode);
        ftp.setServerTimeZoneId(opts, serverTimeZoneId);
        ftp.setShortMonthNames(opts, shortMonthNames);
        ftp.setSoTimeout(opts, socketTimeout);
        ftp.setControlKeepAliveTimeout(opts, controlKeepAliveTimeout);
        ftp.setControlKeepAliveReplyTimeout(opts, controlKeepAliveReplyTimeout);
        ftp.setUserDirIsRoot(opts, userDirIsRoot);
        ftp.setTransferAbortedOkReplyCodes(opts, transferAbortedOkReplyCodes);
        ftp.setMdtmLastModifiedTime(opts, mdtmLastModifiedTime);
        return opts;
    }

    @Override
    protected void loadFetcherFromXML(XML xml) {
        // TODO Auto-generated method stub

    }
    @Override
    protected void saveFetcherToXML(XML xml) {
        // TODO Auto-generated method stub

    }
}
