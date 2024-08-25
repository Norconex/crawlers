/* Copyright 2018-2023 Norconex Inc.
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
package com.norconex.crawler.web.fetch;

import com.norconex.crawler.core.fetch.Fetcher;

/**
 * Fetches HTTP resources.
 * @since 3.0.0
 */
public interface HttpFetcher
        extends Fetcher<HttpFetchRequest, HttpFetchResponse> {
    //    extends AbstractFetcher<HttpFetchRequest, HttpFetchResponse> {
    //
    //    @Override
    //    public HttpFetchResponse fetch(HttpFetchRequest fetchRequest)
    //            throws FetchException {
    //        // TODO Auto-generated method stub
    //        return null;
    //    }
    //
    //    @Override
    //    protected void loadFetcherFromXML(XML xml) {
    //        // TODO Auto-generated method stub
    //
    //    }
    //
    //    @Override
    //    protected void saveFetcherToXML(XML xml) {
    //        // TODO Auto-generated method stub
    //
    //    }

    //TODO do we need this class?  Depends if we need this method:

    //User agent is not obtained part of http fetch response... so no need
    // really.
    //    String getUserAgent();
    //
    //    boolean accept(Doc doc, HttpMethod httpMethod);
    //
    //    /**
    //     * <p>
    //     * Performs an HTTP request for the supplied document reference
    //     * and HTTP method.
    //     * </p>
    //     * <p>
    //     * For each HTTP method supported, implementors should
    //     * do their best to populate the document and its {@link CrawlDocRecord}
    //     * with as much information they can.
    //     * </p>
    //     * <p>
    //     * Unsupported HTTP methods should return an HTTP response with the
    //     * {@link CrawlDocState#UNSUPPORTED} state. To prevent users having to
    //     * configure multiple HTTP clients, implementors should try to support
    //     * both the <code>GET</code> and <code>HEAD</code> methods.
    //     * POST is only used in special cases and is often not used during a
    //     * crawl session.
    //     * </p>
    //     * <p>
    //     * A <code>null</code> method is treated as a <code>GET</code>.
    //     * </p>
    //     * @param doc document to fetch or to use to make the request.
    //     * @param httpMethod HTTP method
    //     * @return an HTTP response
    //     * @throws HttpFetchException problem when fetching the document
    //     * @see HttpFetchResponseBuilder#unsupported()
    //     */
    //    IHttpFetchResponse fetch(CrawlDoc doc, HttpMethod httpMethod)
    //            throws HttpFetchException;

}
