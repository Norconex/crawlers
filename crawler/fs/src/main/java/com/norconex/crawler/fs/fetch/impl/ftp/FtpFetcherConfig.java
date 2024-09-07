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
import com.norconex.crawler.fs.fetch.impl.BaseAuthVfsFetcherConfig;

import jakarta.xml.bind.annotation.XmlTransient;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link FtpFetcher}.
 * </p>
 */
@Data
@Accessors(chain = true)
public class FtpFetcherConfig extends BaseAuthVfsFetcherConfig {

    //TODO once we have all fetchers defined, have them all as default
    // fetchers in config?

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
