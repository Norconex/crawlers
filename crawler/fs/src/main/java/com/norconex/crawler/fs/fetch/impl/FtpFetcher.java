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
import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.ftp.FtpFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.ftp.FtpFileType;
import org.apache.commons.vfs2.provider.ftps.FtpsDataChannelProtectionLevel;
import org.apache.commons.vfs2.provider.ftps.FtpsFileSystemConfigBuilder;
import org.apache.commons.vfs2.provider.ftps.FtpsMode;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.net.ProxySettings;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.fs.fetch.FileFetchRequest;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;

/**
 * <p>
 * FTP (<code>ftp://</code>) and FTPS (<code>ftps://</code>) fetcher.
 * </p>
 *
 * {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractVfsFetcher#doc}
 *
 * <p>
 * XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.crawler.fs.fetch.impl.FtpFetcher">
 *
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 *
 *   {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractVfsFetcher@nx.xml.usage}
 *
 *   <!-- FTP and FTPS -->
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
 *   {@nx.include com.norconex.commons.lang.net.ProxySettings#usage}
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
 *   <!-- FTPS only (applicable only when secure is "true") -->
 *   <secure>[false|true]</secure>
 *   <connectionMode>[EXPLICIT|IMPLICIT]</connectionMode>
 *   <dataChannelProtectionLevel>[C|S|E|P]</dataChannelProtectionLevel>
 *
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <optionsProvider class="FtpFetcher">
 *   <ftpPassiveMode>true</ftpPassiveMode>
 *   <ftpUserDirIsRoot>false</ftpUserDirIsRoot>
 * </optionsProvider>
 * }
 * <p>
 * The above example sets the FTP settings required by some hosts to get file
 * listings on server directories.
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
@FieldNameConstants
@XmlRootElement(name = "fetcher")
@XmlAccessorType(XmlAccessType.FIELD)
public class FtpFetcher extends AbstractVfsFetcher {


    //TODO once we have all fetchers defined, have them all as default
    // fetchers in config

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
    private FtpFileType fileType;
    private boolean mdtmLastModifiedTime;
    private boolean passiveMode;
    @XmlTransient
    private final ProxySettings proxySettings = new ProxySettings();
    private String recentDateFormat;
    private boolean remoteVerification;
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

    // only when secure is true
    private boolean secure;
    private FtpsMode connectionMode;
    private FtpsDataChannelProtectionLevel dataChannelProtectionLevel;

    public List<Integer> getTransferAbortedOkReplyCodes() {
        return Collections.unmodifiableList(transferAbortedOkReplyCodes);
    }
    public final void setTransferAbortedOkReplyCodes(
            List<Integer> transferAbortedOkReplyCodes) {
        CollectionUtil.setAll(
                this.transferAbortedOkReplyCodes, transferAbortedOkReplyCodes);
    }
    public List<String> getShortMonthNames() {
        return Collections.unmodifiableList(shortMonthNames);
    }
    public final void setShortMonthNames(List<String> shortMonthNames) {
        CollectionUtil.setAll(this.shortMonthNames, shortMonthNames);
    }

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return referenceStartsWith(fetchRequest, "ftp://", "ftps://");
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        FtpFileSystemConfigBuilder ftp;
        if (secure) {
            var ftps = FtpsFileSystemConfigBuilder.getInstance();
            ftps.setFtpsMode(opts, connectionMode);
            ftps.setDataChannelProtectionLevel(
                    opts, dataChannelProtectionLevel);
            ftp = ftps;
        } else {
            ftp = FtpFileSystemConfigBuilder.getInstance();
        }
        ftp.setAutodetectUtf8(opts, autodetectUtf8);
        ftp.setConnectTimeout(opts, connectTimeout);
        ftp.setControlEncoding(opts, controlEncoding);
        ftp.setDataTimeout(opts, dataTimeout);
        ftp.setDefaultDateFormat(opts, defaultDateFormat);
        ftp.setFileType(opts, fileType);
        ftp.setPassiveMode(opts, passiveMode);
        ftp.setProxy(opts, proxySettings.toProxy());
        ftp.setRecentDateFormat(opts, recentDateFormat);
        ftp.setRemoteVerification(opts, remoteVerification);
        ftp.setServerLanguageCode(opts, serverLanguageCode);
        ftp.setServerTimeZoneId(opts, serverTimeZoneId);
        ftp.setShortMonthNames(opts,
                shortMonthNames.toArray(EMPTY_STRING_ARRAY));
        ftp.setSoTimeout(opts, socketTimeout);
        ftp.setControlKeepAliveTimeout(opts, controlKeepAliveTimeout);
        ftp.setControlKeepAliveReplyTimeout(opts, controlKeepAliveReplyTimeout);
        ftp.setUserDirIsRoot(opts, userDirIsRoot);
        ftp.setTransferAbortedOkReplyCodes(opts, transferAbortedOkReplyCodes);
        ftp.setMdtmLastModifiedTime(opts, mdtmLastModifiedTime);

        //NOTE: Override this method if there is a need to set additional
        //options, such as entryParserFactory, keyManager, or trustManager.
    }

    @Override
    protected void loadFetcherFromXML(XML xml) {
        super.loadFetcherFromXML(xml);
        xml.ifXML(Fields.proxySettings, proxySettings::loadFromXML);
        setShortMonthNames(xml.getDelimitedList(
                Fields.shortMonthNames, String.class, shortMonthNames));
        setTransferAbortedOkReplyCodes(xml.getDelimitedList(
                Fields.transferAbortedOkReplyCodes,
                Integer.class,
                transferAbortedOkReplyCodes));
    }
    @Override
    protected void saveFetcherToXML(XML xml) {
        super.saveFetcherToXML(xml);
        proxySettings.saveToXML(xml.addElement(Fields.proxySettings));
        xml.addDelimitedElementList(Fields.shortMonthNames, shortMonthNames);
        xml.addDelimitedElementList(Fields.transferAbortedOkReplyCodes,
                transferAbortedOkReplyCodes);
    }
}
