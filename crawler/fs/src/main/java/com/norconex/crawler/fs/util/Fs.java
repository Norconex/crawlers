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
package com.norconex.crawler.fs.util;

import java.util.Collection;
import java.util.List;

import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.pipeline.AbstractPipelineContext;
import com.norconex.crawler.fs.fetch.FileFetcher;

import lombok.NonNull;

public final class Fs {

    private Fs() {}

    public static FileFetcher fetcher(AbstractPipelineContext ctx) {
        return (FileFetcher) ctx.getCrawler().getFetcher();
    }

    public static List<FileFetcher> toFileFetchers(
            @NonNull Collection<Fetcher<?, ?>> fetchers) {
        return fetchers.stream()
            .map(FileFetcher.class::cast)
            .toList();
    }
}
