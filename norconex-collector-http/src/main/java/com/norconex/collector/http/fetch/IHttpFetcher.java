/* Copyright 2018-2019 Norconex Inc.
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

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;

/**
 * Fetches HTTP resources.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public interface IHttpFetcher {

    //TODO have HttpMethod enum class (POST, GET, etc)
    //TODO modify this interface to have:
    //     HttpFetchResponse fetch(HttpMethod method, HttpDocument doc);
    //     and one of:
    //         boolean accept(HttpDocument doc);
    //           or HttpFetchResponse.UNSUPPORTED (singleton?)


    String getUserAgent();

    //TODO remove this method?
    boolean accept(HttpDocument doc);


    IHttpFetchResponse fetchHeaders(String url, HttpMetadata httpHeaders); // throw HttpFetchException
    IHttpFetchResponse fetchDocument(HttpDocument doc);  // throw HttpFetchException
    // INSTEAD?  So we do not expose HttpDocument?
//    HttpFetchResponse fetchDocument(
//            String url, HttpMetadata httpHeaders, OutputStream content);




    //TODO have an collector.HttpFetcherClient (renaming from HttpFetcherExecutor)
    // which is comprised of one or many collector.IHttpFetcher

}
