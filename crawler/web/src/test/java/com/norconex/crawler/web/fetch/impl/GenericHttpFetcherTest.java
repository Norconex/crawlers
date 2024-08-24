/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl;

import static com.norconex.crawler.web.fetch.impl.GenericHttpFetcher.SCHEME_PORT_RESOLVER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.net.URISyntaxException;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.crawler.web.WebTestUtil;

class GenericHttpFetcherTest {

    @Test
    void testWriteRead() {
        var cfg = WebTestUtil.randomize(GenericHttpFetcherConfig.class);
        var f = new GenericHttpFetcher();
        BeanUtil.copyProperties(f.getConfiguration(), cfg);
        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(f));
    }

    @Test
    void testShemePortResolver() throws URISyntaxException {
        assertThat(SCHEME_PORT_RESOLVER.resolve(
                HttpHost.create("http://blah.com"))).isEqualTo(80);
        assertThat(SCHEME_PORT_RESOLVER.resolve(
                HttpHost.create("https://blah.com"))).isEqualTo(443);
        assertThat(SCHEME_PORT_RESOLVER.resolve(
                HttpHost.create("ftp://blah.com"))).isEqualTo(80);
    }
}
