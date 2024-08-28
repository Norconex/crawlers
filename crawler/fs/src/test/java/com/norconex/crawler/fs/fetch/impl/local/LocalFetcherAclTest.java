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
package com.norconex.crawler.fs.fetch.impl.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryFlag;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;

import org.apache.commons.vfs2.provider.local.LocalFile;
import org.apache.commons.vfs2.provider.local.LocalFileName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.fs.doc.FsDocMetadata;

@MockitoSettings
class LocalFetcherAclTest {

    @Mock
    private LocalFile localFile;
    @Mock
    private LocalFileName localFileName;
    @Mock
    private AclFileAttributeView aclView;

    public static LocalFetcher fetcherClient() {
        return new LocalFetcher();
    }

    @Test
    void testAcl() throws IOException {
        when(localFileName.getRootFile()).thenReturn("root-file");
        when(localFileName.getPathDecoded()).thenReturn("path-decoded");
        when(localFile.getName()).thenReturn(localFileName);

        var fetcher = new LocalFetcher();
        var meta = new Properties();

        try (MockedStatic<Files> mocked = mockStatic(Files.class)) {

            // no ACL
            mocked.when(Files.getFileAttributeView(
                    Mockito.any(), Mockito.any())).thenReturn(null);
            fetcher.fetchAcl(localFile, meta);
            assertThat(meta).isEmpty();

            // With ACL
            when(aclView.getOwner()).thenReturn((UserPrincipal) () -> "Joe");
            when(aclView.getAcl()).thenReturn(List.of(AclEntry
                    .newBuilder()
                    .setPrincipal((UserPrincipal) () -> "Jack")
                    .setType(AclEntryType.ALLOW)
                    .setPermissions(
                            AclEntryPermission.APPEND_DATA,
                            AclEntryPermission.READ_ACL)
                    .setFlags(
                            AclEntryFlag.DIRECTORY_INHERIT,
                            AclEntryFlag.FILE_INHERIT)
                    .build()));
            mocked.when(Files.getFileAttributeView(
                    Mockito.any(), Mockito.any())).thenReturn(aclView);
            fetcher.fetchAcl(localFile, meta);

            assertThat(meta.getString(FsDocMetadata.ACL + ".owner"))
                    .isEqualTo("Joe");
            assertThat(meta.getString(
                    FsDocMetadata.ACL + ".ALLOW.APPEND_DATA"))
                            .isEqualTo("Jack");
            assertThat(meta.getString(
                    FsDocMetadata.ACL + ".ALLOW.READ_ACL"))
                            .isEqualTo("Jack");
            assertThat(meta.getString(
                    FsDocMetadata.ACL + ".ALLOW.flag.DIRECTORY_INHERIT"))
                            .isEqualTo("Jack");
            assertThat(meta.getString(
                    FsDocMetadata.ACL + ".ALLOW.flag.FILE_INHERIT"))
                            .isEqualTo("Jack");

            // Test IOException is swallowed
            when(aclView.getOwner()).thenThrow(IOException.class);
            assertThatNoException().isThrownBy(
                    () -> fetcher.fetchAcl(localFile, meta));
        }
    }
}
