/* Copyright 2017-2019 Norconex Inc.
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

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.TestUtil;
import com.norconex.collector.http.fetch.impl.PhantomJSDocumentFetcher.Quality;
import com.norconex.collector.http.fetch.impl.PhantomJSDocumentFetcher.Storage;
import com.norconex.collector.http.fetch.impl.PhantomJSDocumentFetcher.StorageDiskStructure;
import com.norconex.commons.lang.xml.XML;
@Deprecated
public class PhantomJSDocumentFetcherTest  {

    private static final Logger LOG =
            LoggerFactory.getLogger(PhantomJSDocumentFetcherTest.class);

    @Test
    public void testWriteRead() throws IOException {
        PhantomJSDocumentFetcher f = new PhantomJSDocumentFetcher();
        f.setValidStatusCodes(200, 201, 202);
        f.setNotFoundStatusCodes(404, 405);
        f.setHeadersPrefix("blah");
        f.setExePath(new File("/path/to/phantomjs.exe").getAbsolutePath());
        f.setRenderWaitTime(1000);
        f.setResourceTimeout(3000);
        f.setContentTypePattern(".blah.");
        f.setReferencePattern(".blah.blah");
        f.setDetectContentType(true);
        f.setDetectCharset(true);

        f.setScreenshotEnabled(true);
        f.setScreenshotDimensions(30, 40);
        f.setScreenshotImageFormat("gif");
        f.setScreenshotScaleQuality(Quality.HIGH);
        f.setScreenshotScaleDimensions(666, 666);
        f.setScreenshotScaleStretch(true);
        f.setScreenshotStorage(Storage.values());
        f.setScreenshotStorageDiskDir("/path/screenshot");
        f.setScreenshotStorageDiskField("diskField");
        f.setScreenshotStorageDiskStructure(StorageDiskStructure.DATE);
        f.setScreenshotStorageInlineField("inlineField");
        f.setScreenshotZoomFactor(0.5f);

        LOG.info("Writing/Reading this: " + f);
        XML.assertWriteRead(f, "documentFetcher");
    }

    @Test
    public void testValidation() throws IOException {
        TestUtil.testValidation(getClass());
    }
}
