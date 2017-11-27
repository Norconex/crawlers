/* Copyright 2015-2017 Norconex Inc.
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
package com.norconex.collector.http.crawler.redirect.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.norconex.collector.http.redirect.impl.GenericRedirectURLProvider;
import com.norconex.commons.lang.config.XMLConfigurationUtil;

public class GenericRedirectURLProviderTest {

    @Test
    public void testWriteRead() throws IOException {
        GenericRedirectURLProvider p = new GenericRedirectURLProvider();
        p.setFallbackCharset(StandardCharsets.UTF_8.toString());
        System.out.println("Writing/Reading this: " + p);
        XMLConfigurationUtil.assertWriteRead(p);
    }

}
