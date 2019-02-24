/* Copyright 2018 Norconex Inc.
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

    //TODO have HEAD, GET, POST methods instead?

//    HttpFetchResponse fetchDocument(HttpDocument doc);
//    HttpFetchResponse fetchHeaders(String url, HttpMetadata metadata);

    String getUserAgent();
    boolean accept(HttpDocument doc);
    HttpFetchResponse fetchHeaders(String url, HttpMetadata httpHeaders);
    HttpFetchResponse fetchDocument(HttpDocument doc);
    // INSTEAD?  So we do not expose HttpDocument?
//    HttpFetchResponse fetchDocument(
//            String url, HttpMetadata httpHeaders, OutputStream content);

}
