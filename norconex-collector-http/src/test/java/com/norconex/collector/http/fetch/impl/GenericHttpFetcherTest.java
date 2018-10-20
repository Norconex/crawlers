/* Copyright 2015-2018 Norconex Inc.
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
package com.norconex.collector.http.fetch.impl;

import java.io.IOException;

import org.junit.Test;

import com.norconex.commons.lang.xml.XML;

public class GenericHttpFetcherTest  {

    @Test
    public void testWriteRead() throws IOException {
        GenericHttpFetcherConfig cfg = new GenericHttpFetcherConfig();
        cfg.setValidStatusCodes(200, 201, 202);
        cfg.setNotFoundStatusCodes(404, 405);
        cfg.setHeadersPrefix("blah");
        cfg.setDetectCharset(true);
        cfg.setDetectContentType(true);

        GenericHttpFetcher f = new GenericHttpFetcher(cfg);
        XML.assertWriteRead(f, "documentFetcher");
    }
}
