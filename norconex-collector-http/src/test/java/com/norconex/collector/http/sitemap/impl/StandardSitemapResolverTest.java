/* Copyright 2010-2016 Norconex Inc.
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

import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.norconex.commons.lang.config.XMLConfigurationUtil;

public class StandardSitemapResolverTest {

    @Test
    public void testWriteRead() throws IOException {
        StandardSitemapResolverFactory r = new StandardSitemapResolverFactory();
        r.setLenient(true);
        r.setTempDir(new File("C:\\temp\\sitemap"));
        r.setSitemapPaths("/sitemap.xml", "/subdir/sitemap.xml");
        System.out.println("Writing/Reading this: " + r);
        XMLConfigurationUtil.assertWriteRead(r);

        // try with empty paths
        r.setSitemapPaths(new String[] {});
        System.out.println("Writing/Reading this: " + r);
        XMLConfigurationUtil.assertWriteRead(r);

    }

}
