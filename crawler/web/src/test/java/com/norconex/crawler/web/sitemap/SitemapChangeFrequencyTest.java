/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.web.sitemap;

import static com.norconex.crawler.web.sitemap.SitemapChangeFrequency.of;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SitemapChangeFrequencyTest {

    @Test
    void testOf() {
        assertThat(of("always")).isSameAs(SitemapChangeFrequency.ALWAYS);
        assertThat(of("Hourly")).isSameAs(SitemapChangeFrequency.HOURLY);
        assertThat(of("DAILY")).isSameAs(SitemapChangeFrequency.DAILY);
        assertThat(of("WEEKly")).isSameAs(SitemapChangeFrequency.WEEKLY);
        assertThat(of("monthly")).isSameAs(SitemapChangeFrequency.MONTHLY);
        assertThat(of("Yearly")).isSameAs(SitemapChangeFrequency.YEARLY);
        assertThat(of("NEVER")).isSameAs(SitemapChangeFrequency.NEVER);
        assertThat(of(null)).isNull();
        assertThat(of("Bad")).isNull();
    }
}
