/* Copyright 2019-2023 Norconex Inc.
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

import java.util.List;

import com.norconex.crawler.core.fetch.MultiFetchResponse;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * File System multi-response information obtained from fetching a document.
 * Getter methods return values from the last response.
 * @since 3.0.0
 */
@EqualsAndHashCode
@ToString
public class FileMultiFetchResponse
        extends MultiFetchResponse<FileFetchResponse>
        implements FileFetchResponse {

    public FileMultiFetchResponse(List<FileFetchResponse> fetchResponses) {
        super(fetchResponses);
    }

    @Override
    public boolean isFile() {
        return getLastFetchResponse().map(
                FileFetchResponse::isFile).orElse(false);
    }
    @Override
    public boolean isFolder() {
        return getLastFetchResponse().map(
                FileFetchResponse::isFolder).orElse(false);
    }
}
