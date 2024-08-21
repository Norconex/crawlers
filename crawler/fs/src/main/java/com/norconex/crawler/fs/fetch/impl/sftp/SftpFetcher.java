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
package com.norconex.crawler.fs.fetch.impl.sftp;

import static com.norconex.crawler.fs.fetch.impl.FileFetchUtil.referenceStartsWith;

import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;

import com.norconex.commons.lang.time.DurationParser;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * <p>
 * SFTP fetcher.
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
@ToString
@EqualsAndHashCode
public class SftpFetcher extends AbstractAuthVfsFetcher<SftpFetcherConfig> {

    @Getter
    private final SftpFetcherConfig configuration = new SftpFetcherConfig();

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return referenceStartsWith(fetchRequest, "sftp://");
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        var sftp = SftpFileSystemConfigBuilder.getInstance();
        sftp.setCompression(opts, configuration.getCompression());
        sftp.setConnectTimeout(opts, configuration.getConnectTimeout());
        sftp.setKnownHosts(opts, configuration.getKnownHosts());
        sftp.setFileNameEncoding(opts, configuration.getFileNameEncoding());
        sftp.setPreferredAuthentications(
                opts, configuration.getPreferredAuthentications());
        try {
            sftp.setStrictHostKeyChecking(
                    opts, configuration.getStrictHostKeyChecking());
        } catch (FileSystemException e) {
            throw new CrawlerException(e);
        }
        sftp.setUserDirIsRoot(opts, configuration.isUserDirIsRoot());
    }
}
