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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.xml.XML;

public class GenericDocumentFetcherTest  {

    private static final Logger LOG =
            LoggerFactory.getLogger(GenericDocumentFetcherTest.class);

    @Test
    public void testWriteRead() throws IOException {
        GenericDocumentFetcher f = new GenericDocumentFetcher();
        f.setValidStatusCodes(200, 201, 202);
        f.setNotFoundStatusCodes(404, 405);
        f.setHeadersPrefix("blah");
        f.setDetectCharset(true);
        f.setDetectContentType(true);
        LOG.debug("Writing/Reading this: {}", f);
        XML.assertWriteRead(f, "documentFetcher");
    }

}
