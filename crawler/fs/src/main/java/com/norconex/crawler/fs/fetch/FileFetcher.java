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
package com.norconex.crawler.fs.fetch;

import java.util.Set;

import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.fs.path.FsPath;

/**
 * Fetches File System resources.
 * @since 4.0.0
 */
public interface FileFetcher
        extends Fetcher<FileFetchRequest, FileFetchResponse> {

    Set<FsPath> fetchChildPaths(String parentPath) throws FetchException;
}
