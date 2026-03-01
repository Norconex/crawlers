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
package com.norconex.crawler.fs.fetch.impl.archive;

import static com.norconex.crawler.fs.fetch.impl.FileFetchUtil.referenceStartsWith;

import org.apache.commons.vfs2.FileSystemOptions;

import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * <p>
 * Fetcher for archive file systems treated as virtual directory trees.
 * Each entry in the archive is crawled as an individual document, enabling
 * recursive traversal and per-document metadata extraction.
 * </p>
 *
 * <p>
 * This fetcher supports archives that wrap any inner file system, including
 * remote ones such as FTP or SFTP. When credentials are required to access
 * the inner file system (e.g., an FTP server hosting a ZIP file), configure
 * them via the {@link ArchiveFetcherConfig} — VFS automatically forwards them
 * to the inner scheme.
 * </p>
 *
 * <h2>Supported archive schemes (outer layer)</h2>
 * <ul>
 *   <li>{@code zip://} — ZIP archives</li>
 *   <li>{@code jar://} — JAR files</li>
 *   <li>{@code tar://} — TAR archives</li>
 *   <li>{@code tgz://} — Gzip-compressed TAR (alias for {@code tar:gz://})</li>
 *   <li>{@code tbz2://} — Bzip2-compressed TAR (alias for {@code tar:bz2://})</li>
 *   <li>{@code gz://} / {@code gzip://} — Gzip-compressed single files</li>
 *   <li>{@code bz2://} / {@code bzip2://} — Bzip2-compressed single files</li>
 *   <li>{@code mime://} — MIME / e-mail archives (sandbox)</li>
 * </ul>
 *
 * <h2>Example URIs</h2>
 * <ul>
 *   <li>Local: {@code zip:file:///backups/data.zip!/}</li>
 *   <li>FTP: {@code zip:ftp://host/backups/data.zip!/}</li>
 *   <li>SFTP: {@code tgz:sftp://host/exports/dump.tar.gz!/}</li>
 *   <li>HTTP: {@code zip:https://example.com/release.zip!/}</li>
 * </ul>
 *
 * <p>
 * <strong>Note:</strong> For local archive files (i.e., the inner URI is a
 * local path), this fetcher supersedes the archive support previously baked
 * into {@code LocalFetcher}.
 * </p>
 */
@ToString
@EqualsAndHashCode
public class ArchiveFetcher
        extends AbstractAuthVfsFetcher<ArchiveFetcherConfig> {

    @Getter
    private final ArchiveFetcherConfig configuration =
            new ArchiveFetcherConfig();

    @Override
    protected boolean
            acceptFileRequest(@NonNull FileFetchRequest fetchRequest) {
        return referenceStartsWith(
                fetchRequest,
                "bzip2:", "bz2:", "gzip:", "gz:", "jar:",
                "mime:", "tar:", "tgz:", "tbz2:", "zip:");
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        // No archive-specific options; credentials are forwarded to the
        // inner scheme automatically by the parent class via
        // StaticUserAuthenticator set on the shared FileSystemOptions.
    }
}
