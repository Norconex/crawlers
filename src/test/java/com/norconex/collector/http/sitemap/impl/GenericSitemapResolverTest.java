/* Copyright 2019-2020 Norconex Inc.
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
package com.norconex.collector.http.sitemap.impl;

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.xml.XML;

public class GenericSitemapResolverTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            GenericSitemapResolverTest.class);

    @Test
    public void testWriteRead() {
        GenericSitemapResolver r = new GenericSitemapResolver();
        r.setLenient(true);
        r.setTempDir(Paths.get("C:\\temp\\sitemap"));
        r.setSitemapPaths("/sitemap.xml", "/subdir/sitemap.xml");
        LOG.debug("Writing/Reading this: {}", r);
        XML.assertWriteRead(r, "sitemapResolver");

        // try with empty paths
        r.setSitemapPaths(new String[] {});
        LOG.debug("Writing/Reading this: {}", r);
        XML.assertWriteRead(r, "sitemapResolver");
    }

}
