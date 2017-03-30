/* Copyright 2017 Norconex Inc.
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

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.norconex.collector.http.TestUtil;
import com.norconex.commons.lang.config.XMLConfigurationUtil;

public class PhantomJSDocumentFetcherTest  {

    @Test
    public void testWriteRead() throws IOException {
        PhantomJSDocumentFetcher f = new PhantomJSDocumentFetcher();
        f.setValidStatusCodes(200, 201, 202);
        f.setNotFoundStatusCodes(404, 405);
        f.setHeadersPrefix("blah");
        f.setExePath(new File("/path/to/phantomjs.exe").getAbsolutePath());
        f.setRenderWaitTime(1000);
        f.setContentTypePattern(".blah.");
        f.setReferencePattern(".blah.blah");
        System.out.println("Writing/Reading this: " + f);
        XMLConfigurationUtil.assertWriteRead(f);
    }

    @Test
    public void testValidation() throws IOException {
        TestUtil.testValidation(getClass());
    }
}
