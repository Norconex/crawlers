/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.grid.impl.ignite;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class IgniteGridSystemConfig {
    // client only (points to remote Ignite cluster)
    // server only (does not run the crawler, only act as an Ignite database endpoint
    // embedded local (run app and uses ignite locally, not available to other nodes)
    // embedded serve

    //    Client mode:          [My app instance] --> [Ignite instance in Client Mode] --> [Ignite instance in Server Mode]
    //    Embedded client mode: [My app + Ignite Client Mode on same instance] -> [Ignite instance in Server Mode]
}
