/* Copyright 2010-2014 Norconex Inc.
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
package com.norconex.collector.http.recrawl;

/**
 * Indicates whether a document that was successfully crawled on a previous
 * crawling session should be recrawled or not.  Documents not ready 
 * to be recrawled are not downloaded again (no HTTP calls will be made)
 * and are not committed.
 * @author Pascal Essiembre
 * @since 2.5.0
 */
public interface IRecrawlableResolver {

    /**
     * Whether a document recrawlable or not.
     * @param prevCrawlData data about previously crawled document
     * @return <code>true</code> if recrawlable
     */
    boolean isRecrawlable(PreviousCrawlData prevCrawlData);
}
