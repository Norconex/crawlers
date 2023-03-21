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

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.local.LocalFile;

import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.fetch.AbstractFetcher;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.FileFetchResponse;
import com.norconex.crawler.fs.fetch.FileFetcher;
import com.norconex.crawler.fs.path.FsPath;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Base class for fetchers relying on
 * <a href="https://commons.apache.org/proper/commons-vfs/">Apache Commons
 * VFS</a>.
 */
@EqualsAndHashCode
@ToString
public abstract class AbstractVfsFetcher
        extends AbstractFetcher<FileFetchRequest, FileFetchResponse>
        implements FileFetcher {

    @Getter(value = AccessLevel.PACKAGE)
    private StandardFileSystemManager fsManager;

    // Options are unique to each fetcher, making it possible to have
    // multiple file systems of the same type, configured differently.
    @Getter(value = AccessLevel.PACKAGE)
    private FileSystemOptions fsOptions;

    @Override
    protected void fetcherStartup(CrawlSession crawlSession) {
        try {
            fsManager = new StandardFileSystemManager();
            fsManager.setClassLoader(getClass().getClassLoader());
//            if (getCrawlerConfig().getWorkDir() != null) {
//                fileManager.setTemporaryFileStore(new DefaultFileReplicator(
//                       new File(getCrawlerConfig().getWorkDir(), "fvs_cache")));
//            }
            fsManager.init();
        } catch (FileSystemException e) {
            throw new CrawlerException(
                    "Could not initialize file system manager.", e);
        }

        fsOptions = createFileSystemOptions();
    }

    @Override
    public FileFetchResponse fetch(FileFetchRequest fetchRequest)
            throws FetchException {

        if (fsOptions == null) {
            throw new IllegalStateException("This fetcher was not initialized: "
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
            var fileObject = fsManager.resolveFile(ref, fsOptions);

            if (fileObject == null || !fileObject.exists()) {
                return GenericFileFetchResponse.builder()
                        .crawlDocState(CrawlDocState.NOT_FOUND)
                        .build();
            }

            // Don't fetch body if we do meta only
            if (FetchDirective.DOCUMENT.is(fetchRequest.getFetchDirective())) {
                fetchContent(doc, fileObject);
            }

            fetchMeta(doc, fileObject);

            //TODO set status if not found or whatever bad state

            //TODO set content type and content encoding

            return GenericFileFetchResponse.builder()
                .crawlDocState(CrawlDocState.NEW)
                .build();


            //TODO convert to CrawlDocRecord and/or CralDoc depending
            // if we are just fetching headers or both.
        } catch (IOException e) {
            throw new FetchException("Could not fetch reference: " + ref, e);
        }
    }


    @Override
    public Set<FsPath> fetchChildPaths(String parentPath)
            throws FetchException {
        try {
            var fileObject = fsManager.resolveFile(parentPath, fsOptions);
            Set<FsPath> childPaths = new TreeSet<>();
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
            throw new FetchException("Could not fetch child paths of: "
                    + parentPath, e);
        }
    }


    protected abstract FileSystemOptions createFileSystemOptions();

    protected void fetchMeta(CrawlDoc doc, @NonNull FileObject fileObject) {
//        if (!fileObject.exists()) {
//            return FileCrawlState.NOT_FOUND;
//        }
//
//        IFileSpecificMetaFetcher specificFetcher = FILE_SPECIFICS.get(
//                fileObject.getClass());
//        if (specificFetcher != null) {
//            specificFetcher.fetchFileSpecificMeta(fileObject, metadata);
//        }
//
//        FileContent content = fileObject.getContent();
//        //--- Enhance Metadata ---
//        metadata.addLong(
//                FileMetadata.COLLECTOR_SIZE, content.getSize());
//        metadata.addLong(FileMetadata.COLLECTOR_LASTMODIFIED,
//                content.getLastModifiedTime());
//        FileContentInfo info = content.getContentInfo();
//        if (info != null) {
//            metadata.addString(FileMetadata.COLLECTOR_CONTENT_ENCODING,
//                    info.getContentEncoding());
//            metadata.addString(FileMetadata.COLLECTOR_CONTENT_TYPE,
//                    info.getContentType());
//        }
//        for (String attrName: content.getAttributeNames()) {
//            Object obj = content.getAttribute(attrName);
//            if (obj != null) {
//                metadata.addString(FileMetadata.COLLECTOR_PREFIX
//                        + "attribute." + attrName,
//                                Objects.toString(obj));
//            }
//        }
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
