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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.norconex.commons.lang.exec.RetriableException;
import com.norconex.commons.lang.exec.Retrier;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocRecord;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.fetch.MultiFetcher;
import com.norconex.crawler.fs.path.FsPath;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Extends {@link MultiFetcher} only to offer methods that do not
 * necessitate type casting.
 */
@Slf4j
public class FileMultiFetcher
        extends MultiFetcher<FileFetchRequest, FileFetchResponse>
        implements FileFetcher {

    public FileMultiFetcher(
            @NonNull
            List<? extends Fetcher <FileFetchRequest, FileFetchResponse>> fetchers,
            @NonNull
            ResponseListAdapter<FileFetchResponse> multiResponseWrapper,
            @NonNull
            UnsuccessfulResponseFactory
                    <FileFetchResponse> unsuccessfulResponseAdaptor,
            int maxRetries, Duration retryDelay) {
        super(fetchers,
                multiResponseWrapper,
                unsuccessfulResponseAdaptor,
                maxRetries,
                retryDelay);
    }

    @Override
    public Set<FsPath> fetchChildPaths(String parentPath)
            throws FetchException {

        var accepted = false;
        for (Fetcher<FileFetchRequest, FileFetchResponse> fetcher :
                getFetchers()) {

            // Fetch request is fake here, just so we can invoke "accept"
            var req = new FileFetchRequest(
                    new CrawlDoc(new CrawlDocRecord(parentPath)),
                    FetchDirective.DOCUMENT);
            if (!fetcher.accept(req)) {
                continue;
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Child paths fetcher {} accepted this "
                        + "reference: \"{}\".",
                        fetcher.getClass().getSimpleName(), parentPath);
            }
            accepted = true;

            try {
                return new Retrier(getMaxRetries()).execute(() ->
                        ((FileFetcher) fetcher).fetchChildPaths(parentPath));
            } catch (RetriableException e) {
                LOG.debug("Failed to obtain child paths with fetcher: {}.",
                        fetcher.getClass().getName(), e);
            }
        }
        if (!accepted) {
            LOG.debug("""
                No fetcher accepted to fetch this\s\
                reference when fetching children: "{}".\s\
                For generic reference filtering it is highly recommended \s\
                you use a regular reference filtering options, such as \s\
                reference filters.""", parentPath);
        }
        return Collections.emptySet();
    }
}
