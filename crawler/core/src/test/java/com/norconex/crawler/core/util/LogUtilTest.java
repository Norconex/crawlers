/* Copyright 2022-2022 Norconex Inc.
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
package com.norconex.crawler.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class LogUtilTest {

    @Test
    void testMdcUtil() {
        LogUtil.setMdcCrawlerId("Crawler Id");

        assertThat(MDC.get(LogUtil.MDC_CRAWLER_ID)).isEqualTo("Crawler Id");
        assertThat(MDC.get(LogUtil.MDC_CRAWLER_ID_SAFE)).isEqualTo(
                "Crawler_32_Id");
    }
}
