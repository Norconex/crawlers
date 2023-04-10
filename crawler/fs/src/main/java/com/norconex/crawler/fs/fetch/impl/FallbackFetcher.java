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

import org.apache.commons.vfs2.FileSystemOptions;

import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.fs.fetch.FileFetchRequest;

import lombok.Data;
import lombok.NonNull;


/**
 * <p>
 * Fetcher that will try to handle any file system thrown at it with
 * no specific configuration attached. It is meant
 * to be used as the last fetcher in a configured list of fetchers.
 * </p>
 * <p>
 * You should use fetchers dedicated to specific file systems before using this
 * one.  Its usage is discouraged, unless you know your installation
 * supports additional file systems along with their corresponding URI
 * protocols.
 * </p>
 *
 * {@nx.xml.usage
 * <fetcher class="com.norconex.crawler.fs.fetch.impl.FallbackFetcher"
 *     anyFileSystem="[false|true]">
 *   {@nx.include com.norconex.crawler.core.fetch.AbstractFetcher#referenceFilters}
 * </fetcher>
 * }
 *
 * {@nx.xml.example
 * <fetcher class="FallbackFetcher"/>
 * }
 */
@SuppressWarnings("javadoc")
@Data
public class FallbackFetcher extends AbstractVfsFetcher {

    @Override
    protected boolean acceptRequest(@NonNull FileFetchRequest fetchRequest) {
        return true;
    }

    @Override
    protected void applyFileSystemOptions(FileSystemOptions opts) {
        //NOOP
    }

    @Override
    protected void loadFetcherFromXML(XML xml) {
        //NOOP
    }
    @Override
    protected void saveFetcherToXML(XML xml) {
        //NOOP
    }
}
