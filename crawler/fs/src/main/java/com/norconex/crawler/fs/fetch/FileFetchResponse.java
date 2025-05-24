/* Copyright 2023-2025 Norconex Inc.
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

import com.norconex.crawler.core.fetch.FetchResponse;

public interface FileFetchResponse extends FetchResponse {

    /**
     * Whether the fetched path is a file.
     * Note that on some file systems,  it is be possible for a path to
     * represent both a file and folder (a parent file having child files).
     * @return {@code true} if a file
     */
    boolean isFile();

    /**
     * Whether the fetched path a folder.
     * Note that on some file systems,  it is be possible for a path to
     * represent both a file and folder (a parent file having child files).
     * @return {@code true} if a folder
     */
    boolean isFolder();

    //TODO have different file request
    //    /**
    //     * The child paths of a folder path.
    //     * <b>
    //     * Only returned when explicitly requested on {@link FileFetchRequest}.
    //     * </b>
    //     * @return child paths
    //     */
    //    Set<FsPath> childPaths();

}
