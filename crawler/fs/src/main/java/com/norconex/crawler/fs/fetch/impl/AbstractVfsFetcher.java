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

import static com.norconex.crawler.core.doc.CrawlDocMetadata.PREFIX;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.local.LocalFile;
import org.apache.commons.vfs2.util.EncryptUtil;

import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.security.Credentials;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.fetch.AbstractFetcher;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.fs.doc.FsDocMetadata;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.FileFetchResponse;
import com.norconex.crawler.fs.fetch.FileFetcher;
import com.norconex.crawler.fs.path.FsPath;
import com.norconex.importer.doc.DocMetadata;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

/**
 * <p>
 * Base class for fetchers relying on
 * <a href="https://commons.apache.org/proper/commons-vfs/">Apache Commons
 * VFS</a>.
 * </p>
 *
 * <h3>Generic settings</h3>
 * <p>
 * The following is available to all implementing classes.
 * </p>
 *
 * {@nx.xml.usage
 * <!-- Optional authentication details. -->
 * <authentication>
 *   {@nx.include com.norconex.commons.lang.security.Credentials@nx.xml.usage}
 *   <domain>(If required to authenticate, the user's domain.)</domain>
 * </authentication>
 * }
 *
 * {@nx.block #doc
 * {@nx.include com.norconex.commons.lang.security.Credentials#doc}
 * <p>
 * You can also have password set on the URL, Apache
 * Commons VFS offers a way to encrypt it there using their own
 * {@link EncryptUtil}. More info under the "Naming" section here:
 * <a href="http://commons.apache.org/proper/commons-vfs/filesystems.html">
 * http://commons.apache.org/proper/commons-vfs/filesystems.html</a>
 * </p>
 * }
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
@XmlAccessorType(XmlAccessType.NONE)
public abstract class AbstractVfsFetcher
        extends AbstractFetcher<FileFetchRequest, FileFetchResponse>
        implements FileFetcher {

    @Getter(value = AccessLevel.PACKAGE)
    private StandardFileSystemManager fsManager;

    // Options are unique to each fetcher, making it possible to have
    // multiple file systems of the same type, configured differently.
    @Getter(value = AccessLevel.PACKAGE)
    private FileSystemOptions fsOptions;

    // Configurable:
    @Getter
    private final Credentials credentials = new Credentials();
    @Getter
    @Setter
    private String authDomain;

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

        fsOptions = new FileSystemOptions();
        applyDefaultFileSystemOptions(fsOptions);
        applyFileSystemOptions(fsOptions);
    }

    @Override
    protected void fetcherShutdown(CrawlSession collector) {
        if (fsManager != null) {
            fsManager.close();
        }
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
            var fileObject = fsManager.resolveFile(
                    FileFetchUtil.uriEncodeLocalPath(ref), fsOptions);

            if (fileObject == null || !fileObject.exists()) {
                return GenericFileFetchResponse.builder()
                        .crawlDocState(CrawlDocState.NOT_FOUND)
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
                .crawlDocState(CrawlDocState.NEW)
                .file(fileObject.isFile())
                .folder(fileObject.isFolder())
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
            throw new FetchException("Could not fetch child paths of: "
                    + parentPath, e);
        }
    }

    /**
     * Applies options that exists in each Commons VFS implementations.
     * @param opts file system options
     */
    protected void applyDefaultFileSystemOptions(FileSystemOptions opts) {
        var defBuilder = DefaultFileSystemConfigBuilder.getInstance();
        if (credentials.isSet()) {
            defBuilder.setUserAuthenticator(opts, new StaticUserAuthenticator(
                    authDomain,
                    credentials.getUsername(),
                    EncryptionUtil.decryptPassword(credentials)));
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
            meta.set(DocMetadata.CONTENT_ENCODING, info.getContentEncoding());
            meta.set(DocMetadata.CONTENT_TYPE, info.getContentType());
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

    @Override
    protected void loadFetcherFromXML(XML xml) {
        xml.ifXML("authentication", authXml -> {
            authXml.ifXML("credentials", credentials::loadFromXML);
            setAuthDomain(authXml.getString("domain", authDomain));
        });
    }
    @Override
    protected void saveFetcherToXML(XML xml) {
        var authXml = xml.addElement("authentication");
        credentials.saveToXML(authXml.addElement("credentials"));
        authXml.addElement("domain", authDomain);
    }
}
