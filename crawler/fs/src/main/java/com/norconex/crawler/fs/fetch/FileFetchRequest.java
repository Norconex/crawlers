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
package com.norconex.crawler.fs.fetch;

import javax.annotation.Nonnull;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchRequest;

import lombok.Data;

//TODO move to crawler-core as a generic implementation?
@Data
public class FileFetchRequest implements FetchRequest {

    @Nonnull
    private final CrawlDoc doc;

    private final FetchDirective fetchDirective;
}
