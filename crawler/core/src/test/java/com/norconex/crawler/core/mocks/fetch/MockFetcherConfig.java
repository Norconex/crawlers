/* Copyright 2022-2026 Norconex Inc.
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
package com.norconex.crawler.core.mocks.fetch;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.norconex.crawler.core.fetch.BaseFetcherConfig;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MockFetcherConfig extends BaseFetcherConfig {

    private Boolean denyRequest;
    private boolean returnBadStatus;
    private boolean randomDocContent;
    private Duration delay;
    /**
     * When {@code true}, every fetch throws a {@link
     * com.norconex.crawler.core.fetch.FetchException}.
     * Note: this exception is caught by {@code MultiFetcher} and converted to
     * an ERROR response — it does NOT propagate to the crawl pipeline.
     * Use {@link #isThrowOnAccept()} to cause a pipeline-escaping exception.
     */
    private boolean throwFetchException;
    /**
     * Throw a {@link com.norconex.crawler.core.fetch.FetchException} only
     * for the listed references.  Has no effect when
     * {@link #isThrowFetchException()} is {@code true}.
     * Note: caught by {@code MultiFetcher}, does not escape the pipeline.
     */
    private List<String> throwOnRefs = new ArrayList<>();
    /**
     * When {@code true}, {@code acceptRequest()} throws a
     * {@link RuntimeException} for every reference.  Unlike
     * {@link #isThrowFetchException()}, this exception is NOT caught by
     * {@code MultiFetcher} and propagates to {@code processNextInQueue}'s
     * catch block, triggering
     * {@code handleExceptionAndCheckIfStopCrawler}.
     */
    private boolean throwOnAccept;
    /**
     * Throw a {@link RuntimeException} in {@code acceptRequest()} only for
     * the listed references when {@link #isThrowOnAccept()} is {@code false}.
     */
    private List<String> throwOnAcceptRefs = new ArrayList<>();
}
