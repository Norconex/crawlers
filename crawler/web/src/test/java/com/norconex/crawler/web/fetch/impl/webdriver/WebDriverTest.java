/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl.webdriver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.stubs.CrawlDocStubs;

class WebDriverTest {

    @Test
    void testUnsupportedHttpMethod() throws FetchException {
        var response = new WebDriverFetcher().fetch(
                new HttpFetchRequest(
                        CrawlDocStubs.crawlDocHtml("http://example.com"),
                        HttpMethod.HEAD));
        assertThat(response.getReasonPhrase()).contains("To obtain headers");
        assertThat(response.getResolutionStatus()).isEqualTo(
                CrawlDocStatus.UNSUPPORTED);
    }

}
