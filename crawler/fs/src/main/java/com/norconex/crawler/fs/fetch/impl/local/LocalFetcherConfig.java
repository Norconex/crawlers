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

import com.norconex.crawler.fs.fetch.impl.BaseAuthVfsFetcherConfig;

import lombok.Data;
import lombok.experimental.Accessors;

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
 *   <li>MIME ({@code mime://})</li>
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
@Accessors(chain = true)
public class LocalFetcherConfig extends BaseAuthVfsFetcherConfig {

    private boolean aclDisabled;

    //    @Override
    //    protected void loadFetcherFromXML(XML xml) {
    //        setAclDisabled(xml.getBoolean(Fields.aclDisabled));
    //    }
    //    @Override
    //    protected void saveFetcherToXML(XML xml) {
    //        xml.addElement(Fields.aclDisabled, aclDisabled);
    //    }
}
