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
package com.norconex.crawler.server.api.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AppConfigTest {

    @Test
    void testAppConfig() {
        var defaultPath = "/api/v1";
        var resolvedPath = "https://somehost.com/my-api";

        var env = new MockEnvironment();

        assertThat(env.resolveRequiredPlaceholders(
                AppConfig.REQUEST_MAPPING_API_V1)).isEqualTo(defaultPath);

        env.setProperty("openapi.nx-crawler-server.base-path", resolvedPath);
        assertThat(env.resolveRequiredPlaceholders(
                AppConfig.REQUEST_MAPPING_API_V1)).isEqualTo(resolvedPath);

    }
}
