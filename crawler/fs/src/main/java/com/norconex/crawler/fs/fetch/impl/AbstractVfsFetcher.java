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
package com.norconex.crawler.fs.fetch.impl;

import static com.norconex.crawler.core.doc.CrawlDocMetaConstants.PREFIX;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.local.LocalFile;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.fetch.AbstractFetcher;
import com.norconex.crawler.core.fetch.BaseFetcherConfig;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.fs.doc.FsDocMetadata;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.FileFetchResponse;
import com.norconex.crawler.fs.fetch.FileFetcher;
import com.norconex.crawler.fs.path.FsPath;
import com.norconex.importer.doc.DocMetaConstants;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * <p>
 * Base class for fetchers relying on
 * <a href="https://commons.apache.org/proper/commons-vfs/">Apache Commons
 * VFS</a>.
 * </p>
 * @param <C> configuration type
 * @see AbstractAuthVfsFetcher
 */
@EqualsAndHashCode
@ToString
public abstract class AbstractVfsFetcher<C extends BaseFetcherConfig>
        extends AbstractFetcher<FileFetchRequest, FileFetchResponse, C>
        implements FileFetcher {

    @Getter(value = AccessLevel.PACKAGE)
    private StandardFileSystemManager fsManager;

    // Options are unique to each fetcher, making it possible to have
    // multiple file systems of the same type, configured differently.
    @Getter(value = AccessLevel.PACKAGE)
    private FileSystemOptions fsOptions;

    @Override
    protected void fetcherStartup(CrawlerContext crawler) {
        try {
            fsManager = new StandardFileSystemManager();
            fsManager.setClassLoader(getClass().getClassLoader());
            fsManager.init();
        } catch (FileSystemException e) {
            throw new CrawlerException(
                    "Could not initialize file system manager.", e);
        }

        fsOptions = new FileSystemOptions();
        applyFileSystemOptions(fsOptions);
    }

    @Override
    protected void fetcherShutdown(CrawlerContext crawler) {
        if (fsManager != null) {
            fsManager.close();
        }
    }

    @Override
    public FileFetchResponse fetch(FileFetchRequest fetchRequest)
            throws FetchException {

        if (fsOptions == null) {
            throw new IllegalStateException(
                    "This fetcher was not initialized: "
                            + getClass().getName());
        }

        var doc = fetchRequest.getDoc();

        // if meta, we do not copy body.
        //
        // if body, we copy both meta and body, but meta is copied
        // as overwrites or optional (we do not carry multi values for meta
        // obtained from having both META and BODY request types)

        var ref = doc.getReference();
        try {
            var fileObject = fsManager.resolveFile(
                    FileFetchUtil.uriEncodeLocalPath(ref), fsOptions);

            if (fileObject == null || !fileObject.exists()) {
                return GenericFileFetchResponse.builder()
                        .resolutionStatus(CrawlDocStatus.NOT_FOUND)
                        .build();
            }

            if (fileObject.isFile()) {
                // Don't fetch body if we do meta only
                if (FetchDirective.DOCUMENT.is(
                        fetchRequest.getFetchDirective())) {
                    fetchContent(doc, fileObject);
                }
                fetchMetadata(doc, fileObject);
            }

            //TODO set status if not found or whatever bad state

            return GenericFileFetchResponse.builder()
                    .resolutionStatus(CrawlDocStatus.NEW)
                    .file(fileObject.isFile())
                    .folder(fileObject.isFolder())
                    .build();
        } catch (IOException e) {
            throw new FetchException("Could not fetch reference: " + ref, e);
        }
    }

    @Override
    public Set<FsPath> fetchChildPaths(String parentPath)
            throws FetchException {
        try {
            var fileObject = fsManager.resolveFile(
                    FileFetchUtil.uriEncodeLocalPath(parentPath), fsOptions);
            Set<FsPath> childPaths = new HashSet<>();
            for (var childPath : fileObject.getChildren()) {

                // Special chars such as # can be valid in local
                // file names, so get path from toString on local files,
                // which returns the unencoded path (github #47).
                var ref = childPath.getName().getURI();
                if (childPath instanceof LocalFile) {
                    ref = childPath.getName().toString();
                }
                var path = new FsPath(ref);
                path.setFile(childPath.isFile());
                path.setFolder(childPath.isFolder());
                childPaths.add(path);
            }
            return childPaths;
        } catch (FileSystemException e) {
            throw new FetchException(
                    "Could not fetch child paths of: "
                            + parentPath,
                    e);
        }
    }

    /**
     * Applies options specific to this Commons VFS implementations.
     * @param opts file system options
     */
    protected abstract void applyFileSystemOptions(FileSystemOptions opts);

    protected void fetchMetadata(CrawlDoc doc, @NonNull FileObject fileObject)
            throws FileSystemException {

        var content = fileObject.getContent();
        var meta = doc.getMetadata();
        //--- Enhance Metadata ---
        meta.set(FsDocMetadata.FILE_SIZE, content.getSize());
        meta.set(FsDocMetadata.LAST_MODIFIED, content.getLastModifiedTime());
        var info = content.getContentInfo();
        if (info != null) {
            meta.set(DocMetaConstants.CONTENT_ENCODING, info.getContentEncoding());
            meta.set(DocMetaConstants.CONTENT_TYPE, info.getContentType());
        }
        content.getAttributes().forEach((k, v) -> {
            if (v != null) {
                meta.add(PREFIX + "attribute." + k, Objects.toString(v));
            }
        });

        meta.set(PREFIX + "executable", fileObject.isExecutable());
        meta.set(PREFIX + "hidden", fileObject.isHidden());
        meta.set(PREFIX + "readable", fileObject.isReadable());
        meta.set(PREFIX + "symbolicLink", fileObject.isSymbolicLink());
        meta.set(PREFIX + "writable", fileObject.isWriteable());
    }

    protected boolean fetchContent(CrawlDoc doc, @NonNull FileObject fileObject)
            throws IOException {
        var content = fileObject.getContent();
        if (content == null) {
            return false;
        }
        try (var is = doc.getStreamFactory().newInputStream(
                content.getInputStream())) {
            is.enforceFullCaching();
            doc.setInputStream(is);
        }
        return true;
    }
}
