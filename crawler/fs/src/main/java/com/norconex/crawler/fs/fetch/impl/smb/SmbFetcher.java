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
package com.norconex.crawler.fs.fetch.impl.smb;

import static com.norconex.crawler.fs.fetch.impl.FileFetchUtil.referenceStartsWith;

import java.io.IOException;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.UserAuthenticationData;
import org.apache.commons.vfs2.UserAuthenticationData.Type;
import org.apache.commons.vfs2.provider.smb.SmbFileName;
import org.apache.commons.vfs2.provider.smb.SmbFileObject;
import org.apache.commons.vfs2.provider.smb.SmbFileProvider;
import org.apache.commons.vfs2.util.UserAuthenticatorUtils;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.fs.doc.FsDocMetadata;
import com.norconex.crawler.fs.fetch.FileFetchRequest;
import com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher;

import jcifs.smb.ACE;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * CIFS fetcher (Samba, Windows share).
 * </p>
 *
 * {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher#doc}
 *
 * <h3>Access Control List (ACL)</h3>
 * <p>
 * This fetcher will try to extract access control information for each
 * SMB file. If you have no need for them, you can disable
 * acquiring them with {@link #setAclDisabled(boolean)}.
 * </p>
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.crawler.fs.fetch.impl.smb.SmbFetcher">
 *
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 *
 *   {@nx.include com.norconex.crawler.fs.fetch.impl.AbstractAuthVfsFetcher@nx.xml.usage}
 *
 *   <aclDisabled>[false|true]</aclDisabled>
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <fetcher class="SmbFetcher">
 *   <authentication>
 *     <username>joe</username>
 *     <password>joe's-password</password>
 *     <domain>WORKGROUP</domain>
 *   </authentication>
 * </fetcher>
 * }
 */
@SuppressWarnings("javadoc")
@ToString
@EqualsAndHashCode
@Slf4j
public class SmbFetcher extends AbstractAuthVfsFetcher<SmbFetcherConfig> {

    @Getter
    private final SmbFetcherConfig configuration = new SmbFetcherConfig();

    private static final String ACL_PREFIX =
            FsDocMetadata.ACL + ".smb";
    private static final String ACE = ".ace";
    private static final String SID = ".sid";
    private static final String SID_TEXT = ".sidAsText";
    private static final String TYPE = ".type";
    private static final String TYPE_TEXT = ".typeAsText";
    private static final String DOMAIN_SID = ".domainSid";
    private static final String DOMAIN_NAME = ".domainName";
    private static final String ACCOUNT_NAME = ".accountName";

    @Override
    protected void fetchMetadata(CrawlDoc doc, @NonNull FileObject fileObject)
            throws FileSystemException {
        super.fetchMetadata(doc, fileObject);

        // Fetch ACL
        if (!configuration.isAclDisabled()
                && fileObject instanceof SmbFileObject smbFileObject) {
            try {
                var f = createSmbFile(smbFileObject);
                var acl = f.getSecurity();
                storeSID(acl, doc.getMetadata());
            } catch (IOException e) {
                LOG.error("Could not retreive SMB ACL data.", e);
            }
        }
    }

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return referenceStartsWith(fetchRequest, "smb://");
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        //NOOP
    }

    private void storeSID(ACE[] acls, Properties metadata) {
        for (var i = 0; i < acls.length; i++) {
            var acl = acls[i];
            var sid = acl.getSID();
            metaSet(metadata, i, ACE, acl);
            metaSet(metadata, i, SID, sid);
            metaSet(metadata, i, SID_TEXT, sid.toDisplayString());
            metaSet(metadata, i, TYPE, sid.getType());
            metaSet(metadata, i, TYPE_TEXT, sid.getTypeText());
            metaSet(metadata, i, DOMAIN_SID, sid.getDomainSid());
            metaSet(metadata, i, DOMAIN_NAME, sid.getDomainName());
            metaSet(metadata, i, ACCOUNT_NAME, sid.getAccountName());
        }
    }
    private void metaSet(
            Properties metadata, int index, String suffix, Object value) {
        var v = StringUtils.trimToNull(Objects.toString(value, null));
        if (v != null) {
            metadata.set(key(index, suffix), v);
        }
    }
    private String key(int index, String suffix) {
        return ACL_PREFIX + "[" + index + "]" + suffix;
    }

    /*
     * Adapted from SmbFileObject since there is otherwise no way to get
     * the SmbFile from the Commons VFS sandbox SmbFile class and we need it
     * for ACL extract.  Should delete if a better way is provider by VFS.
     */
    private SmbFile createSmbFile(FileObject fileObject)
            throws IOException {

        final var smbFileName = (SmbFileName) fileObject.getName();

        final var path = smbFileName.getUriWithoutAuth();

        UserAuthenticationData authData = null;
        SmbFile file;
        try {
            authData = UserAuthenticatorUtils.authenticate(
                    fileObject.getFileSystem().getFileSystemOptions(),
                           SmbFileProvider.AUTHENTICATOR_TYPES);

            NtlmPasswordAuthentication auth = null;
            if (authData != null) {
                auth = new NtlmPasswordAuthentication(
                        authToString(authData, UserAuthenticationData.DOMAIN,
                                smbFileName.getDomain()),
                        authToString(authData, UserAuthenticationData.USERNAME,
                                smbFileName.getUserName()),
                        authToString(authData, UserAuthenticationData.PASSWORD,
                                smbFileName.getPassword()));
            }

            // if auth == null SmbFile uses default credentials
            file = new SmbFile(path, auth);

            if (file.isDirectory() && !file.toString().endsWith("/")) {
                file = new SmbFile(path + "/", auth);
            }
            return file;
        } finally {
            UserAuthenticatorUtils.cleanup(authData); // might be null
        }
    }

    private String authToString(
            UserAuthenticationData authData, Type type, String part) {
        return UserAuthenticatorUtils.toString(UserAuthenticatorUtils.getData(
                authData, type, UserAuthenticatorUtils.toChar(part)));
    }
}
