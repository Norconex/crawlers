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
package com.norconex.crawler.fs.fetch.impl.local;

import static com.norconex.crawler.fs.fetch.impl.FileFetchUtil.referenceStartsWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclFileAttributeView;
import java.util.Objects;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.provider.local.LocalFile;
import org.apache.commons.vfs2.provider.local.LocalFileName;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.fs.doc.FsDocMetadata;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractVfsFetcher;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;


/**
 * <p>
 * Fetcher for a local file system. Mounted file systems and mapped drives
 * not requiring special configuration to access can also be considered
 * "local". Paths starting with any of the following will be recognized as
 * local file system:
 * </p>
 * <ul>
 *   <li>{@code file:///some/directory}</li>
 *   <li>{@code file:///C:/some/directory}</li>
 *   <li>{@code /some/directory}</li>
 *   <li>{@code C:\some\directory}</li>
 *   <li>{@code C:/some/directory}</li>
 * </ul>
 *
 * <h3>Access Control List (ACL)</h3>
 * <p>
 * This fetcher will try to extract access control information for each file
 * of a local file system. If you have no need for them, you can disable
 * acquiring them with {@link #setAclDisabled(boolean)}.
 * </p>
 *
 * <h3>Archive files as file systems</h3>
 * <p>
 * This fetcher can also treat local archive files as local file
 * systems. Supported local archives file systems (and their schemes):
 * </p>
 * <ul>
 *   <li>bzip2 ({@code bzip2://})</li>
 *   <li>gzip ({@code gzip://})</li>
 *   <li>Jar ({@code jar://})</li>
 *   <li>Tar ({@code tar://}, {@code tgz://}, {@code tbz2://})</li>
 *   <li>Zip ({@code zip://})</li>
 * </ul>
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.crawler.fs.fetch.impl.local.LocalFetcher">
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 *   <aclDisabled>[false|true]</aclDisabled>
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <fetcher class="LocalFileFetcher"/>
 * }
 */
@SuppressWarnings("javadoc")
@Data
@FieldNameConstants
@Slf4j
public class LocalFetcher extends AbstractVfsFetcher {

    private boolean aclDisabled;

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return referenceStartsWith(
            fetchRequest,
            "/", "\\", "file:", "bzip2:", "gzip:", "jar:",
            "tar:", "tgz:", "tbz2:", "zip:"
        ) || fetchRequest.getDoc().getDocRecord().getReference().matches(
                "(?i)^[a-z]{1,2}:[/\\\\].*");
    }

    @Override
    protected void fetchMetadata(CrawlDoc doc, @NonNull FileObject fileObject)
            throws FileSystemException {
        super.fetchMetadata(doc, fileObject);

        if (!aclDisabled && fileObject instanceof LocalFile localFile) {
            fetchAcl(localFile, doc.getMetadata());
        }
    }

    private void fetchAcl(LocalFile localFile, Properties metadata) {
        try {
            var localFileName = (LocalFileName) localFile.getName();
            var file = new File(localFileName.getRootFile()
                    + localFileName.getPathDecoded()).toPath();

            var aclFileAttributes = Files.getFileAttributeView(
                    file, AclFileAttributeView.class);

            if (aclFileAttributes == null) {
                LOG.debug("No ACL file attributes on " + file);
                return;
            }

            if (aclFileAttributes.getOwner() != null
                    && aclFileAttributes.getOwner().getName() != null) {
                metadata.add(FsDocMetadata.ACL + ".owner",
                        aclFileAttributes.getOwner().getName());
            }

            for (AclEntry aclEntry : aclFileAttributes.getAcl()) {
                var type = Objects.toString(aclEntry.type(), "[NOTYPE]");
                var principal = aclEntry.principal().getName();
                for (AclEntryPermission perm : aclEntry.permissions()) {
                    metadata.add(FsDocMetadata.ACL + "." + type
                            + "." + perm.name(), principal);
                }
                for (AclEntryFlag flag : aclEntry.flags()) {
                    metadata.add(FsDocMetadata.ACL + "." + type
                            + ".flag." + flag.name(), principal);
                }
            }
        } catch (IOException e) {
            LOG.error("Could not retreive ACL data.", e);
        }
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        //NOOP
    }

    @Override
    protected void loadFetcherFromXML(XML xml) {
        setAclDisabled(xml.getBoolean(Fields.aclDisabled));
    }
    @Override
    protected void saveFetcherToXML(XML xml) {
        xml.addElement(Fields.aclDisabled, aclDisabled);
    }
}
