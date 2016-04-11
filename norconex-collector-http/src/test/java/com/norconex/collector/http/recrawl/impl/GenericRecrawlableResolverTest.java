/* Copyright 2016 Norconex Inc.
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
package com.norconex.collector.http.recrawl.impl;

import java.io.IOException;

import org.junit.Test;

import com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver.MinFrequency;
import com.norconex.collector.http.recrawl.impl.GenericRecrawlableResolver.SitemapSupport;
import com.norconex.commons.lang.config.ConfigurationUtil;

public class GenericRecrawlableResolverTest {

    @Test
    public void testWriteRead() throws IOException {
        GenericRecrawlableResolver r = new GenericRecrawlableResolver();
        r.setSitemapSupport(SitemapSupport.LAST);
        
        MinFrequency f1 = new MinFrequency("reference", "monthly", ".*\\.pdf");
        MinFrequency f2 = new MinFrequency("contentType", "1234", ".*");
        f2.setCaseSensitive(true);
        
        r.setMinFrequencies(f1, f2);

        System.out.println("Writing/Reading this: " + r);
        ConfigurationUtil.assertWriteRead(r);
    }
}
