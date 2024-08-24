/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
* Generic context useful for storing arbitrary objects in memory.
* Crawler implementations can also provide their own subclass
* with concrete methods and properties instead of the attribute accessors
* of this class.
* Class mainly makes it easier for developers to store ad-hoc data in
* their own extensions of an existing crawler implementation
* so multiple components can easily share said data when needed.
*/
@EqualsAndHashCode
@ToString
public class CrawlerContext {
   private final Map<String, Object> attributes = new HashMap<>();

   public Optional<Object> getAttribute(String key) {
       return Optional.ofNullable(attributes.get(key));
   }
   public Optional<Object> setAttribute(String key, Object obj) {
       return Optional.ofNullable(attributes.put(key, obj));
   }
}
