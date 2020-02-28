/* Copyright 2018-2020 Norconex Inc.
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
package com.norconex.collector.http.fetch;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.importer.doc.Doc;

/**
 * Fetches HTTP resources.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public interface IHttpFetcher {

    


    //TODO have HttpMethod enum class (POST, GET, etc)
    //TODO modify this interface to have:
    //     HttpFetchResponse fetch(HttpMethod method, Doc doc);
    //     and one of:
    //         boolean accept(Doc doc);
    //           or HttpFetchResponse.UNSUPPORTED (singleton?)


    String getUserAgent();

    //TODO remove this method?
    boolean accept(Doc doc);

    /**
     * <p>
     * Performs an HTTP request for the supplied document reference
     * and HTTP method.
     * </p>
     * <p>
     * For each method supported, implementors should
     * do their best to populate the supplied {@link CrawlDoc} the best
     * they can.
     * </p>
     * <p>
     * Unsupported HTTP methods should return an HTTP response with the
     * {@link CrawlState#UNSUPPORTED} state. To prevent userse having to
     * configure multiple HTTP clients, implementors should try to support
     * both the <code>GET</code> and <code>HEAD</code> methods.
     * POST is only used in special cases and is often not used during a
     * crawl session.
     * </p>
     * <p>
     * A <code>null</code> method is treated as a <code>GET</code>.
     * </p>
     * @param doc document to fetch or to use to make the request.
     * @param httpMethod HTTP method
     * @return an HTTP response
     * @throws HttpFetchException problem when fetching the document
     * @see {@link HttpFetchResponseBuilder#unsupported()}
     */
    IHttpFetchResponse fetch(CrawlDoc doc, HttpMethod httpMethod)
            throws HttpFetchException;

}
