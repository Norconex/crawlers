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

import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.auth.StaticUserAuthenticator;
import org.apache.commons.vfs2.impl.DefaultFileSystemConfigBuilder;
import org.apache.commons.vfs2.util.EncryptUtil;

import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.crawler.core.CrawlerContext;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Extension of {@link AbstractVfsFetcher}, adding authentication support.
 * </p>
 *
 * <h3>Generic authentication settings</h3>
 *
 * <p>
 * You can also have password set on the URL, Apache
 * Commons VFS offers a way to encrypt it there using their own
 * {@link EncryptUtil}. More info under the "Naming" section here:
 * <a href="http://commons.apache.org/proper/commons-vfs/filesystems.html">
 * http://commons.apache.org/proper/commons-vfs/filesystems.html</a>
 * </p>
 * @param <C> Type of configuration class
 *
 */
@EqualsAndHashCode
@ToString
public abstract class AbstractAuthVfsFetcher<C extends BaseAuthVfsFetcherConfig>
        extends AbstractVfsFetcher<C> {

    @Override
    protected void fetcherStartup(CrawlerContext crawler) {
        super.fetcherStartup(crawler);
        applyAuthenticationOptions(getFsOptions());
    }

    /**
     * Applies options that exists in each Commons VFS implementations.
     * @param opts file system options
     */
    protected void applyAuthenticationOptions(FileSystemOptions opts) {
        var defBuilder = DefaultFileSystemConfigBuilder.getInstance();
        if (getConfiguration().getCredentials().isSet()) {
            defBuilder.setUserAuthenticator(
                    opts, new StaticUserAuthenticator(
                            getConfiguration().getDomain(),
                            getConfiguration().getCredentials().getUsername(),
                            EncryptionUtil.decryptPassword(
                                    getConfiguration().getCredentials())));
        }
    }
}
