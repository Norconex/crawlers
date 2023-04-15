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
package com.norconex.crawler.fs.fetch.impl.sftp;

import static com.norconex.crawler.fs.fetch.impl.FileFetchUtil.referenceStartsWith;

import java.io.File;
import java.time.Duration;

import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;

import com.norconex.commons.lang.time.DurationParser;
import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;

/**
 * <p>
 * SFTP fetcher.
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
 * <fetcher class="com.norconex.crawler.fs.fetch.impl.sftp.SftpFetcher">
 *
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 *
 *   {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher@nx.xml.usage}
 *
 *   <compression>...</compression>
 *   <fileNameEncoding>...</fileNameEncoding>
 *   <knownHosts>...</knownHosts>
 *   <preferredAuthentications>...</preferredAuthentications>
 *   <strictHostKeyChecking>[no|yes|ask]</strictHostKeyChecking>
 *   <connectTimeout>(milliseconds or as text)</connectTimeout>
 *   <userDirIsRoot>[false|true]</userDirIsRoot>
 *
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <fetcher class="SftpFetcher">
 *   <timeout>2 minutes</timeout>
 * </fetcher>
 * }
 * <p>
 * The above example the SFTP time out to 2 minutes.
 * </p>
 */
@SuppressWarnings("javadoc")
@Data
@FieldNameConstants
@XmlRootElement(name = "fetcher")
@XmlAccessorType(XmlAccessType.FIELD)
public class SftpFetcher extends AbstractAuthVfsFetcher {

    private String compression;
    private String fileNameEncoding;
    private File knownHosts;
    private String preferredAuthentications;
    private String strictHostKeyChecking = "no";
    private Duration connectTimeout;
    private boolean userDirIsRoot;

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return referenceStartsWith(fetchRequest, "sftp://");
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        var sftp = SftpFileSystemConfigBuilder.getInstance();
        sftp.setCompression(opts, compression);
        sftp.setConnectTimeout(opts, connectTimeout);
        sftp.setKnownHosts(opts, knownHosts);
        sftp.setPreferredAuthentications(opts, preferredAuthentications);
        try {
            sftp.setStrictHostKeyChecking(opts, strictHostKeyChecking);
        } catch (FileSystemException e) {
            throw new CrawlerException(e);
        }
        sftp.setUserDirIsRoot(opts, userDirIsRoot);
    }
}
